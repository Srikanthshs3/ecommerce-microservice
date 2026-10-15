package org.praskanth.seed;

import org.praskanth.util.SlugUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The starter product taxonomy: 3 levels, Amazon style.
 *
 *   Level 1  department   Men's, Women's, Kids, Fashion, Mobiles &amp; Computers, ...
 *   Level 2  category     Shirts, Jeans, Shoes, Smartphones, Laptops, ...
 *   Level 3  brand / type Van Heusen, Levi's, Samsung, Dell, ...   (products attach here)
 *
 * It is plain Java on purpose: no Spring, so it can be tested and reused. To change the starter data, edit this file
 * (or, in production, manage categories through the admin screen / API: this is only the first-run seed).
 */
public final class CategoryTaxonomy {

    /** A node in the starter tree. Level is implied by depth. */
    public record Node(String name, String icon, List<Node> children) {
    }

    /** A node flattened in pre-order (parents always come before their children). */
    public record Flat(String name, String icon, String slug, String slugPath, int level, int displayOrder,
                       int parentIndex, int childCount) {
    }

    private CategoryTaxonomy() {
    }

    // ---- tiny DSL to keep the data readable ------------------------------------------------------------------
    private static Node dept(String name, String icon, Node... groups) {
        return new Node(name, icon, List.of(groups));
    }

    private static Node group(String name, String... brands) {
        List<Node> leaves = new ArrayList<>();
        for (String brand : brands) {
            leaves.add(new Node(brand, null, List.of()));
        }
        return new Node(name, null, leaves);
    }

    public static List<Node> departments() {
        return List.of(
                dept("Men's", "\uD83D\uDC54",
                        group("T-Shirts & Polos", "Puma", "Nike", "U.S. Polo Assn.", "Levi's", "Roadster", "HRX"),
                        group("Shirts", "Van Heusen", "Allen Solly", "Peter England", "Raymond", "Louis Philippe", "Arrow"),
                        group("Jeans", "Levi's", "Wrangler", "Lee", "Pepe Jeans", "Flying Machine", "Jack & Jones"),
                        group("Trousers & Chinos", "Louis Philippe", "Park Avenue", "Raymond", "Allen Solly", "Peter England"),
                        group("Shoes", "Nike", "Adidas", "Puma", "Bata", "Woodland", "Red Tape"),
                        group("Innerwear", "Jockey", "Calvin Klein", "Van Heusen", "Dollar", "Macroman M-Series")),

                dept("Women's", "\uD83D\uDC57",
                        group("Kurtas & Suits", "Biba", "W", "Aurelia", "Libas", "Global Desi"),
                        group("Sarees", "Taneira", "Nalli", "Soch", "Anouk", "Sangria"),
                        group("Tops & T-Shirts", "Vero Moda", "Only", "H&M", "Zara", "Max"),
                        group("Jeans & Jeggings", "Levi's", "Only", "Vero Moda", "Pepe Jeans", "Lee"),
                        group("Footwear", "Bata", "Crocs", "Skechers", "Metro", "Catwalk", "Nike")),

                dept("Kids", "\uD83E\uDDF8",
                        group("Boys' Clothing", "Gini & Jony", "Allen Solly Junior", "Puma", "U.S. Polo Assn. Kids"),
                        group("Girls' Clothing", "Biba Girls", "Gini & Jony", "Allen Solly Junior", "Pepe Jeans Kids"),
                        group("Toys & Games", "LEGO", "Hot Wheels", "Barbie", "Funskool", "Hasbro"),
                        group("School Supplies", "Classmate", "Camlin", "Faber-Castell", "Nataraj", "Apsara"),
                        group("Baby Care", "Pampers", "Huggies", "Himalaya Baby", "Johnson's Baby", "Mamaearth")),

                dept("Fashion", "\uD83D\uDC5C",
                        group("Watches", "Titan", "Fastrack", "Casio", "Fossil", "Timex", "Sonata"),
                        group("Bags & Luggage", "Wildcraft", "American Tourister", "Skybags", "Safari", "Samsonite"),
                        group("Jewellery", "Tanishq", "Kalyan Jewellers", "CaratLane", "Malabar Gold", "GIVA"),
                        group("Sunglasses", "Ray-Ban", "Fastrack", "Vincent Chase", "Oakley", "Carrera"),
                        group("Wallets & Belts", "Woodland", "Hidesign", "Tommy Hilfiger", "Wildhorn", "Allen Solly")),

                dept("Mobiles & Computers", "\uD83D\uDCBB",
                        group("Smartphones", "Samsung", "Apple", "OnePlus", "Xiaomi", "Realme", "Vivo", "Oppo"),
                        group("Laptops", "Dell", "HP", "Lenovo", "Apple", "Asus", "Acer"),
                        group("Tablets", "Apple", "Samsung", "Lenovo", "Xiaomi"),
                        group("Computer Accessories", "Logitech", "HP", "Dell", "Zebronics", "Redragon"),
                        group("Storage & Networking", "SanDisk", "Seagate", "Western Digital", "TP-Link", "D-Link")),

                dept("Electronics", "\uD83D\uDCFA",
                        group("Televisions", "Sony", "LG", "Samsung", "Mi", "TCL"),
                        group("Headphones & Earphones", "boAt", "Sony", "JBL", "Bose", "Noise"),
                        group("Speakers", "JBL", "boAt", "Sony", "Bose", "Marshall"),
                        group("Cameras", "Canon", "Nikon", "Sony", "GoPro", "Fujifilm"),
                        group("Wearables", "Apple", "Samsung", "Noise", "boAt", "Fire-Boltt")),

                dept("Home & Kitchen", "\uD83C\uDFE0",
                        group("Kitchen Appliances", "Philips", "Prestige", "Bajaj", "Morphy Richards", "Butterfly"),
                        group("Cookware", "Prestige", "Hawkins", "Pigeon", "Vinod", "Milton"),
                        group("Furniture", "Godrej Interio", "Nilkamal", "Wakefit", "Urban Ladder", "Durian"),
                        group("Home Decor", "IKEA", "Home Centre", "Story@Home", "Pepperfry"),
                        group("Bedding", "Wakefit", "Sleepwell", "Bombay Dyeing", "Spaces", "Sleepyhead")),

                dept("Beauty & Health", "\uD83D\uDC84",
                        group("Skin Care", "Nivea", "Lakm\u00e9", "Mamaearth", "Cetaphil", "Neutrogena"),
                        group("Hair Care", "L'Or\u00e9al", "Dove", "TRESemm\u00e9", "Himalaya", "WOW Skin Science"),
                        group("Makeup", "Maybelline", "Lakm\u00e9", "MAC", "Nykaa", "Revlon"),
                        group("Health & Wellness", "Himalaya", "Dabur", "Patanjali", "HealthKart", "Horlicks"),
                        group("Personal Grooming", "Philips", "Gillette", "Braun", "Havells", "Park Avenue")),

                dept("Books", "\uD83D\uDCDA",
                        group("Fiction", "Penguin", "HarperCollins", "Bloomsbury", "Westland", "Rupa"),
                        group("Non-Fiction", "Penguin", "HarperCollins", "Jaico", "Manjul", "Hay House"),
                        group("Academic & Exams", "Arihant", "Oxford", "Cambridge", "McGraw Hill", "S. Chand"),
                        group("Children's Books", "Scholastic", "Disney", "Puffin", "Amar Chitra Katha", "Wonder House"),
                        group("Comics & Manga", "Marvel", "DC", "Viz Media", "Dark Horse")),

                dept("Sports & Fitness", "\u26BD",
                        group("Cricket", "SG", "SS", "MRF", "Kookaburra", "Gray-Nicolls"),
                        group("Football", "Nivia", "Adidas", "Nike", "Puma", "Cosco"),
                        group("Gym & Fitness", "Decathlon", "Boldfit", "Cultsport", "Strauss", "Fitkit"),
                        group("Cycling", "Hero", "Hercules", "Firefox", "Btwin", "Giant"),
                        group("Outdoor & Camping", "Wildcraft", "Quechua", "Decathlon", "Coleman")));
    }

    /**
     * Flattens the tree in pre-order so a seeder can insert parents before children.
     * displayOrder is the position among siblings * 10 (leaves room to insert between two items later).
     */
    public static List<Flat> flatten(List<Node> roots) {
        List<Flat> out = new ArrayList<>();
        walk(roots, -1, "", 1, out);
        return out;
    }

    private static void walk(List<Node> nodes, int parentIndex, String parentSlugPath, int level, List<Flat> out) {
        int order = 0;
        for (Node node : nodes) {
            order += 10;
            String slug = SlugUtil.slugify(node.name());
            String slugPath = parentSlugPath.isEmpty() ? slug : parentSlugPath + "/" + slug;
            int index = out.size();
            out.add(null); // reserve the slot so children get a higher index than their parent
            walk(node.children(), index, slugPath, level + 1, out);
            out.set(index, new Flat(node.name(), node.icon(), slug, slugPath, level, order, parentIndex, node.children().size()));
        }
    }
}
