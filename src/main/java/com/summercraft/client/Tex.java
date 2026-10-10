package com.summercraft.client;

/**
 * Regions of the car texture atlas ({@code textures/entity/car.png}): a 256x256 sheet of 32x32 tiles,
 * numbered left to right, top to bottom. tools/gen_textures.py paints the same tiles in the same order.
 */
public enum Tex {
    PAINT, CHROME, TREAD, RIM, CAST_IRON, ALUMINIUM, RED_METAL, BLACK_PLASTIC,
    FABRIC, RADIATOR, BATTERY, GLASS, HEADLIGHT, TAILLIGHT, GRILLE, FRAME,
    CERAMIC, EXHAUST, DASH, BOLT, BRAKE_DISC, INTERIOR, COPPER, TYRE_WALL,
    RUBBER, FUEL_TANK, INDICATOR, WHITE, TREAD_SLICK, TREAD_OFFROAD, LIFT;

    private static final float TILE = 32f / 256f;

    public float u0() {
        return (ordinal() % 8) * TILE;
    }

    public float v0() {
        return (ordinal() / 8) * TILE;
    }

    public float size() {
        return TILE;
    }
}
