package com.example.radiation;

import org.bukkit.Location;

public class RadiationZone {
    private String name;
    private String world;
    private double x1, y1, z1;
    private double x2, y2, z2;
    
    public RadiationZone(String name, String world, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.name = name;
        this.world = world;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }
    
    public String getName() {
        return name;
    }
    
    public String getWorld() {
        return world;
    }
    
    // Возвращаем минимальные координаты
    public double getX1() { return Math.min(x1, x2); }
    public double getY1() { return Math.min(y1, y2); }
    public double getZ1() { return Math.min(z1, z2); }
    // Возвращаем максимальные координаты
    public double getX2() { return Math.max(x1, x2); }
    public double getY2() { return Math.max(y1, y2); }
    public double getZ2() { return Math.max(z1, z2); }
    
    // Проверка, находится ли локация внутри зоны
    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(world)) return false;
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= getX1() && x <= getX2() &&
               y >= getY1() && y <= getY2() &&
               z >= getZ1() && z <= getZ2();
    }
}
