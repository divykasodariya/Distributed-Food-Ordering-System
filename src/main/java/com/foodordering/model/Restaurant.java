package com.foodordering.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Restaurant implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final Map<String, Double> menu;

    public Restaurant(String id, String name, Map<String, Double> menu) {
        this.id = id;
        this.name = name;
        this.menu = menu != null ? new LinkedHashMap<>(menu) : new LinkedHashMap<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Double> getMenu() { return Collections.unmodifiableMap(menu); }

    public static Map<String, Restaurant> getDefaultRestaurants() {
        Map<String, Restaurant> restaurants = new LinkedHashMap<>();

        Map<String, Double> menuA = new LinkedHashMap<>();
        menuA.put("Margherita Pizza", 12.99);
        menuA.put("Pepperoni Pizza", 14.99);
        menuA.put("Garlic Bread", 5.49);
        restaurants.put("Restaurant A", new Restaurant("REST-1", "Restaurant A (Pizza Palace)", menuA));

        Map<String, Double> menuB = new LinkedHashMap<>();
        menuB.put("Classic Burger", 9.99);
        menuB.put("Cheese Fries", 4.99);
        menuB.put("Milkshake", 4.50);
        restaurants.put("Restaurant B", new Restaurant("REST-2", "Restaurant B (Burger Bar)", menuB));

        return restaurants;
    }
}
