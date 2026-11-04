package fr.pepsgoal.core

/** Shared editorial taxonomy; it does not change prices, ownership or discovery. */
enum class ShopCategory(val label: String) {
    SIGNATURE("PepsGoal"), STADIUM("Stades"), CITY("Villes et voyages"),
    NATURE("Nature et paysages"), SPACE("Espace"), ABSTRACT("Abstraits et couleurs"),
    SEASON("Saisons et fêtes"), FOOD("Douceurs"), SYMBOL("Symboles et objets")
}

fun shopCategory(key: String, season: String? = null): ShopCategory = when {
    season != null || key in setOf("haunted", "snowvillage", "fireworks", "snowpeak", "heartsky", "eggmeadow", "flowerfield", "summerbeach", "autumnwood", "xmastree", "valentine", "citrouille") -> ShopCategory.SEASON
    key in setOf("pepsgoal", "default", "pop", "system") -> ShopCategory.SIGNATURE
    key in setOf("sco", "srfc", "psg", "fcb", "fcn", "kopa", "ecran", "trophee") -> ShopCategory.STADIUM
    key in setOf("angers", "angevine", "loire", "loiresummer", "nantes", "tram", "tramway", "chateau", "citynight", "rooftops", "eiffel", "metro", "nighttrain", "tunnel", "velo", "vinyle") -> ShopCategory.CITY
    key in setOf("nebula", "planet", "cosmos", "galaxy", "fusee", "lune", "stars") -> ShopCategory.SPACE
    key in setOf("mountains", "mistforest", "forest", "foret", "dunes", "deepsea", "abyss", "ocean", "sakura", "bamboo", "bambou", "lagoon", "lighthouse", "phare", "vineyard", "balloons", "montgolfiere", "jellyfish", "meduse", "canyon", "savanna", "icefield", "lanterns", "magma", "lave", "rainwindow", "sky", "borealis", "aurore", "sommet", "pousse", "emeraude", "emerald", "sand", "basalt", "midnight") -> ShopCategory.NATURE
    key in setOf("candy", "cotton", "matcha", "wine", "pistachio", "lemon", "peach", "cocoa", "cafe", "glace", "mint") -> ShopCategory.FOOD
    key in setOf("grid", "mesh", "blob", "waves", "aurora", "coderain", "neon", "paper", "sepia", "graphite", "plum", "ink", "steel", "nordic", "coral", "linen", "lavender", "synthwave", "pixel", "mono", "dark", "gold", "eclair", "cristal", "arcenciel") -> ShopCategory.ABSTRACT
    else -> ShopCategory.SYMBOL
}

val ThemePack.category: ShopCategory get() = if (isDefault) ShopCategory.SIGNATURE else shopCategory(bgKey, colourTheme?.season)
