package com.foodloop.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Categories each NGO accepts. An NGO without rows accepts every category. */
@Repository
public class NgoAcceptedCategoryRepository {

    private final JdbcTemplate jdbc;

    public NgoAcceptedCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** ngoId to category ids, only for NGOs that saved a list. */
    public Map<Long, Set<Integer>> findAll() {
        Map<Long, Set<Integer>> out = new HashMap<>();
        jdbc.query("SELECT ngo_id, category_id FROM ngo_accepted_categories", (RowCallbackHandler) rs ->
                out.computeIfAbsent(rs.getLong(1), k -> new TreeSet<>()).add(rs.getInt(2)));
        return out;
    }

    public Set<Integer> find(Long ngoId) {
        return new TreeSet<>(jdbc.queryForList(
                "SELECT category_id FROM ngo_accepted_categories WHERE ngo_id = ?", Integer.class, ngoId));
    }

    public int countExisting(Collection<Integer> categoryIds) {
        if (categoryIds.isEmpty()) {
            return 0;
        }
        String marks = String.join(",", Collections.nCopies(categoryIds.size(), "?"));
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM food_categories WHERE id IN (" + marks + ")",
                Integer.class, categoryIds.toArray());
        return n == null ? 0 : n;
    }

    public void replace(Long ngoId, Collection<Integer> categoryIds) {
        jdbc.update("DELETE FROM ngo_accepted_categories WHERE ngo_id = ?", ngoId);
        for (Integer id : categoryIds) {
            jdbc.update("INSERT INTO ngo_accepted_categories (ngo_id, category_id) VALUES (?, ?)", ngoId, id);
        }
    }
}