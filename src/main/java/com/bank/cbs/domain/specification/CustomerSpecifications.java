package com.bank.cbs.domain.specification;

import com.bank.cbs.domain.entity.Customer;
import com.bank.cbs.domain.enums.CustomerStatus;
import org.springframework.data.jpa.domain.Specification;

public final class CustomerSpecifications {

    private CustomerSpecifications() {}

    public static Specification<Customer> withStatus(CustomerStatus status) {
        return (root, query, cb) ->
            status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Customer> matchingSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String lowerPattern = "%" + search.trim().toLowerCase() + "%";
            String rawPattern = "%" + search.trim() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("fullName")), lowerPattern),
                cb.like(cb.lower(root.get("email")), lowerPattern),
                cb.like(root.get("phone"), rawPattern)
            );
        };
    }
}