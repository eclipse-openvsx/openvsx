/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.admin;

import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.Tier;
import org.eclipse.openvsx.entities.TierType;
import org.eclipse.openvsx.entities.UsageStats;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.ratelimit.cache.RateLimitCacheService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;


@RestController
@RequestMapping("/admin/ratelimit")
public class RateLimitAPI {
    private final Logger logger = LoggerFactory.getLogger(RateLimitAPI.class);

    private final RepositoryService repositories;
    private final AdminService admins;
    private final LogService logs;
    private RateLimitCacheService rateLimitCacheService;

    public RateLimitAPI(
            RepositoryService repositories,
            AdminService admins,
            LogService logs,
            Optional<RateLimitCacheService> rateLimitCacheService
    ) {
        this.repositories = repositories;
        this.admins = admins;
        this.logs = logs;
        rateLimitCacheService.ifPresent(service -> this.rateLimitCacheService = service);
    }

    @GetMapping(
            path = "/tiers",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TierListJson> getTiers() {
        try {
            admins.checkAdminUser();

            var tiers = repositories.findAllTiers();
            var result = new TierListJson(tiers.stream().map(Tier::toJson).toList());
            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed retrieving tiers", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(
            path = "/tiers/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TierJson> createTier(@RequestBody TierJson tier) {
        try {
            var adminUser = admins.checkAdminUser();

            var existingTier = repositories.findTier(tier.getName());
            if (existingTier != null) {
                return ResponseEntity.badRequest().body(TierJson.error("Tier with name " + tier.getName() + " already exists"));
            }

            var tierType = TierType.valueOf(tier.getTierType());
            if (tierType != TierType.NON_FREE) {
                var existingTiers = repositories.findTiersByTierType(TierType.valueOf(tier.getTierType()));
                if (!existingTiers.isEmpty()) {
                    return ResponseEntity.badRequest().body(TierJson.error("Tier with type '" + tier.getTierType() + "' already exists"));
                }
            }

            var savedTier = repositories.upsertTier(Tier.fromJson(tier));

            var result = savedTier.toJson();
            result.setSuccess("Created tier '" + savedTier.getName() + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TIER);
            }

            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed creating tier {}", tier.getName(), exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping(
            path = "/tiers/{name}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TierJson> updateTier(@PathVariable String name, @RequestBody TierJson tier) {
        try {
            var adminUser = admins.checkAdminUser();

            var savedTier = repositories.findTier(name);
            if (savedTier == null) {
                return ResponseEntity.notFound().build();
            }

            var tierType = TierType.valueOf(tier.getTierType());
            if (tierType != TierType.NON_FREE) {
                var existingTiers = repositories.findTiersByTierTypeExcludingTier(TierType.valueOf(tier.getTierType()), savedTier);
                if (!existingTiers.isEmpty()) {
                    return ResponseEntity.badRequest().body(TierJson.error("Tier with type '" + tier.getTierType() + "' already exists"));
                }
            }

            savedTier.updateFromJson(tier);
            savedTier = repositories.upsertTier(savedTier);

            var result = savedTier.toJson();
            result.setSuccess("Updated tier '" + savedTier.getName() + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TIER);
            }

            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed updating tier {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping(
            path = "/tiers/{name}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> deleteTier(@PathVariable String name) {
        try {
            var adminUser = admins.checkAdminUser();

            var tier = repositories.findTier(name);
            if (tier == null) {
                return ResponseEntity.notFound().build();
            }

            var existingCustomers = repositories.countCustomersByTier(tier);
            if (existingCustomers > 0) {
                return ResponseEntity.badRequest().body(ResultJson.error("Cannot delete tier '" + name + "' because it is still in use"));
            }

            repositories.deleteTier(tier);

            var result = ResultJson.success("Deleted tier '" + name + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TIER);
            }

            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed deleting tier {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/tiers/{name}/customers",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerListJson> getCustomersForTier(@PathVariable String name) {
        try {
            admins.checkAdminUser();

            var tier = repositories.findTier(name);
            if (tier == null) {
                return ResponseEntity.notFound().build();
            }

            var existingCustomers = repositories.findCustomersByTier(tier);
            var result = new CustomerListJson(existingCustomers.stream().map(Customer::toJson).toList());
            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed getting customers for tier {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/customers",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerListJson> getCustomers() {
        try {
            admins.checkAdminUser();

            var customers = repositories.findAllCustomers();
            var result = new CustomerListJson(customers.stream().map(Customer::toJson).toList());
            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed retrieving customers", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/customers/{name}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerJson> getCustomer(@PathVariable String name) {
        try {
            admins.checkAdminUser();

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(customer.toJson());
        } catch (Exception exc) {
            logger.error("failed retrieving customer {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(
            path = "/customers/create",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerJson> createCustomer(@RequestBody CustomerJson customerJson) {
        try {
            var adminUser = admins.checkAdminUser();

            var existingCustomer = repositories.findCustomer(customerJson.getName());
            if (existingCustomer != null) {
                return ResponseEntity
                            .badRequest()
                            .body(CustomerJson.error("Customer with name " + customerJson.getName() + " already exists"));
            }

            var customer = Customer.fromJson(customerJson);
            // resolve the tier reference
            var tier = repositories.findTier(customer.getTier().getName());
            if (tier == null) {
                return ResponseEntity
                            .badRequest()
                            .body(CustomerJson.error("Tier with name " + customer.getTier().getName() + " does not exist"));
            }
            customer.setTier(tier);

            var savedCustomer = repositories.upsertCustomer(customer);

            var result = savedCustomer.toJson();
            result.setSuccess("Created customer '" + savedCustomer.getName() + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_CUSTOMER);
            }

            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed creating customer {}", customerJson.getName(), exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping(
            path = "/customers/{name}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerJson> updateCustomer(@PathVariable String name, @RequestBody CustomerJson customer) {
        try {
            var adminUser = admins.checkAdminUser();

            var savedCustomer = repositories.findCustomer(name);
            if (savedCustomer == null) {
                return ResponseEntity.notFound().build();
            }

            savedCustomer.updateFromJson(customer);
            // update the tier reference in case it changed
            var tier = repositories.findTier(savedCustomer.getTier().getName());
            if (tier == null) {
                return ResponseEntity
                            .badRequest()
                            .body(CustomerJson.error("Tier with name " + customer.getTier().getName() + " does not exist"));
            }
            savedCustomer.setTier(tier);

            savedCustomer = repositories.upsertCustomer(savedCustomer);

            var result = savedCustomer.toJson();
            result.setSuccess("Updated customer '" + savedCustomer.getName() + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_CUSTOMER);
            }

            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed updating tier {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping(
            path = "/customers/{name}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> deleteCustomer(@PathVariable String name) {
        try {
            var adminUser = admins.checkAdminUser();

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            repositories.deleteCustomer(customer);

            var result = ResultJson.success("Deleted customer '" + name + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_CUSTOMER);
            }

            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed deleting customer {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/customers/{name}/usage",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UsageStatsListJson> getUsageStats(@PathVariable String name, @RequestParam(required = false) String date) {
        try {
            admins.checkAdminUser();

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            var localDateTime = date != null ? TimeUtil.fromUTCString(date) : TimeUtil.getCurrentUTC();
            var stats = repositories.findUsageStatsByCustomerAndDate(customer, localDateTime);
            var result = new UsageStatsListJson(stats.stream().map(UsageStats::toJson).toList());
            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed retrieving usage stats", exc);
            return ResponseEntity.internalServerError().build();
        }
    }
}
