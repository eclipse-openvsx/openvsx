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

import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.ratelimit.CustomerService;
import org.eclipse.openvsx.ratelimit.cache.RateLimitCacheService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.LogService;
import org.eclipse.openvsx.util.NotFoundException;
import org.eclipse.openvsx.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/admin/ratelimit")
public class RateLimitAPI {
    private final Logger logger = LoggerFactory.getLogger(RateLimitAPI.class);

    private static final int TOKEN_DESCRIPTION_SIZE = 255;

    private final RepositoryService repositories;
    private final AdminService admins;
    private final LogService logs;
    private final CustomerService customerService;
    private RateLimitCacheService rateLimitCacheService;

    public RateLimitAPI(
            RepositoryService repositories,
            AdminService admins,
            LogService logs,
            CustomerService customerService,
            Optional<RateLimitCacheService> rateLimitCacheService
    ) {
        this.repositories = repositories;
        this.admins = admins;
        this.logs = logs;
        this.customerService = customerService;
        rateLimitCacheService.ifPresent(service -> this.rateLimitCacheService = service);
    }

    @GetMapping(
            path = "/tiers",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TierListJson> getTiers() {
        admins.checkAdminUser();

        try {
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
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TIER, Long.toString(savedTier.getId()));
            }

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(TierJson.class);
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
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TIER, Long.toString(savedTier.getId()));
            }

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(TierJson.class);
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
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TIER, Long.toString(tier.getId()));
            }

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
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
        admins.checkAdminUser();

        try {
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
        admins.checkAdminUser();

        try {
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
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(CustomerJson.class);
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
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_CUSTOMER, Long.toString(savedCustomer.getId()));
            }

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(CustomerJson.class);
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
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_CUSTOMER, Long.toString(savedCustomer.getId()));
            }

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(CustomerJson.class);
        } catch (Exception exc) {
            logger.error("failed updating tier {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/customers/{name}/members",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CustomerMembershipListJson> getCustomerMembers(@PathVariable String name) {
        try {
            admins.checkAdminUser();

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            var memberships = repositories.findCustomerMemberships(customer);
            var membershipList = new CustomerMembershipListJson();
            membershipList.setCustomerMemberships(memberships.stream().map(CustomerMembership::toJson).toList());
            return ResponseEntity.ok(membershipList);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(CustomerMembershipListJson.class);
        } catch (Exception exc) {
            logger.error("failed retrieving customer members {}", name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(
            path = "/customers/{name}/add-member",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> addCustomerMember(
            @PathVariable String name,
            @RequestParam("user") String userName,
            @RequestParam(required = false) String provider
    ) {
        try {
            var admin = admins.checkAdminUser();

            var result = customerService.addCustomerMember(name, userName, provider);
            logs.logAction(admin, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        } catch (Exception exc) {
            logger.error("failed adding user {} to customer {}", userName, name, exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(
            path = "/customers/{name}/remove-member",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> removeCustomerMember(
            @PathVariable String name,
            @RequestParam("user") String userName,
            @RequestParam(required = false) String provider
    ) {
        try {
            var admin = admins.checkAdminUser();

            var result = customerService.removeCustomerMember(name, userName, provider);
            logs.logAction(admin, result);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        } catch (Exception exc) {
            logger.error("failed removing user {} from customer {}", userName, name, exc);
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

            // get the list of active rate limit tokens for this customer
            var activeRateLimitTokenIds = repositories
                    .findActiveRateLimitTokens(customer)
                    .stream()
                    .map(token -> Long.toString(token.getId()))
                    .toList();

            repositories.deleteCustomer(customer);

            var result = ResultJson.success("Deleted customer '" + name + "'");
            logs.logAction(adminUser, result);

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_CUSTOMER, Long.toString(customer.getId()));

                if (!activeRateLimitTokenIds.isEmpty()) {
                    rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TOKEN, String.join(",", activeRateLimitTokenIds));
                }
            }

            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
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
        admins.checkAdminUser();

        try {
            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            var localDateTime = date != null ? TimeUtil.fromUTCString(date) : TimeUtil.getCurrentUTC();
            var stats = repositories.findUsageStatsByCustomerAndDate(customer, localDateTime);
            var dailyStats = repositories.findDailyUsageStats(customer, localDateTime.toLocalDate());
            var dailyP95 = dailyStats != null ? dailyStats.getP95Requests() : null;
            var result = new UsageStatsListJson(stats.stream().map(UsageStats::toJson).toList(), dailyP95);
            return ResponseEntity.ok(result);
        } catch (Exception exc) {
            logger.error("failed retrieving usage stats", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(
            path = "/customers/{name}/tokens",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<RateLimitTokenJson>> getRateLimitTokens(@PathVariable String name) {
        admins.checkAdminUser();

        try {
            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            repositories.findActiveRateLimitTokens(customer);
            var tokens = repositories.findActiveRateLimitTokens(customer)
                    .map(RateLimitToken::toJson)
                    .toList();

            return ResponseEntity.ok(tokens);
        } catch (Exception exc) {
            logger.error("failed retrieving rate limit tokens", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(
            path = "/customers/{name}/tokens",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RateLimitTokenJson> createRateLimitToken(
            @PathVariable String name,
            @RequestParam(required = false) String description
    ) {
        try {
            admins.checkAdminUser();

            if (description != null && description.length() > TOKEN_DESCRIPTION_SIZE) {
                var json = RateLimitTokenJson.error("The description must not be longer than " + TOKEN_DESCRIPTION_SIZE + " characters.");
                return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
            }

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            return new ResponseEntity<>(customerService.createRateLimitToken(customer, description), HttpStatus.CREATED);
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity(RateLimitTokenJson.class);
        } catch (Exception exc) {
            logger.error("failed creating rate limit token", exc);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping(
            path = "/customers/{name}/tokens/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> deactivateRateLimitToken(@PathVariable String name, @PathVariable long id) {
        try {
            admins.checkAdminUser();

            var customer = repositories.findCustomer(name);
            if (customer == null) {
                return ResponseEntity.notFound().build();
            }

            var token = repositories.findRateLimitToken(id);
            if (token == null || !token.isActive()) {
                return ResponseEntity.notFound().build();
            }

            if (token.getCustomer().getId() != customer.getId()) {
                return ResponseEntity.notFound().build();
            }

            var response = ResponseEntity.ok(customerService.deactivateRateLimitToken(token));

            if (rateLimitCacheService != null) {
                rateLimitCacheService.publishConfigUpdate(RateLimitCacheService.CACHE_TOKEN, token.getValue());
            }

            return response;
        } catch (ErrorResultException exc) {
            return exc.toResponseEntity();
        } catch (NotFoundException exc) {
            return new ResponseEntity<>(ResultJson.error("Rate limit token does not exist."), HttpStatus.NOT_FOUND);
        } catch (Exception exc) {
            logger.error("failed deactivating rate limit token", exc);
            return ResponseEntity.internalServerError().build();
        }
    }
}
