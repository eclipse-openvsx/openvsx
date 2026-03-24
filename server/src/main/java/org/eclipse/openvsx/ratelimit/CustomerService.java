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
package org.eclipse.openvsx.ratelimit;

import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4AddressAssociativeTrie;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.Customer;
import org.eclipse.openvsx.entities.CustomerMembership;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.ratelimit.cache.ConfigurationChanged;
import org.eclipse.openvsx.ratelimit.cache.RateLimitCacheService;
import org.eclipse.openvsx.ratelimit.config.RateLimitConfig;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerService {

    private final Logger logger = LoggerFactory.getLogger(CustomerService.class);

    private final EntityManager entityManager;
    private final RepositoryService repositories;
    private IPv4AddressAssociativeTrie<Customer> customersByIPAddress;

    public CustomerService(EntityManager entityManager, RepositoryService repositories) {
        this.entityManager = entityManager;
        this.repositories = repositories;
    }

    @PostConstruct
    private void init() {
        customersByIPAddress = rebuildIPAddressCache();
    }

    @Cacheable(value = RateLimitCacheService.CACHE_CUSTOMER, key = "'id_' + #id", cacheManager = RateLimitCacheService.CACHE_MANAGER)
    public Optional<Customer> getCustomerById(long id) {
        return repositories.findCustomerById(id);
    }

    public Optional<Customer> getCustomerByIpAddress(String ipAddress) {
        var ip = new IPAddressString(ipAddress).getAddress().toIPv4();
        if (ip == null) {
            logger.warn("Could not determine IP address from string {}", ipAddress);
            return Optional.empty();
        }
        
        var node = customersByIPAddress.elementsContaining(ip);
        if (node != null) {
            return Optional.of(node.getValue());
        } else {
            return Optional.empty();
        }
    }

    @EventListener
    public void refreshCache(ConfigurationChanged event) {
        logger.debug("Rebuilding IP Address cache");
        customersByIPAddress = rebuildIPAddressCache();
    }

    private IPv4AddressAssociativeTrie<Customer> rebuildIPAddressCache() {
        var trie = new IPv4AddressAssociativeTrie<Customer>();

        for (Customer customer : repositories.findAllCustomers()) {
            for (String cidrBlock : customer.getCidrBlocks()) {
                var ipAddress = new IPAddressString(cidrBlock).getAddress().toIPv4();
                trie.put(ipAddress, customer);
            }
        }
        return trie;
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson addCustomerMember(String customerName, String userName, String provider) throws ErrorResultException {
        var customer = repositories.findCustomer(customerName);
        if (customer == null) {
            throw new ErrorResultException("Customer not found: " + customerName);
        }
        var user = repositories.findUserByLoginName(provider, userName);
        if (user == null) {
            throw new ErrorResultException("User not found: " + (provider + "/" + userName));
        }

        var membership = repositories.findCustomerMembership(user, customer);
        if (membership != null) {
            throw new ErrorResultException("User " + user.getLoginName() + " is already member of customer " + customer.getName() + ".");
        }

        membership = new CustomerMembership();
        membership.setCustomer(customer);
        membership.setUser(user);
        entityManager.persist(membership);
        return ResultJson.success("Added " + user.getLoginName() + " as member of customer " + customer.getName() + ".");
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson removeCustomerMember(String customerName, String userName, String provider) throws ErrorResultException {
        var customer = repositories.findCustomer(customerName);
        if (customer == null) {
            throw new ErrorResultException("Customer not found: " + customerName);
        }
        var user = repositories.findUserByLoginName(provider, userName);
        if (user == null) {
            throw new ErrorResultException("User not found: " + (provider + "/" + userName));
        }

        var membership = repositories.findCustomerMembership(user, customer);
        if (membership == null) {
            throw new ErrorResultException("User " + user.getLoginName() + " is not a member of customer " + customer.getName() + ".");
        }

        entityManager.remove(membership);
        return ResultJson.success("Removed " + user.getLoginName() + " as member of customer " + customer.getName() + ".");
    }
}
