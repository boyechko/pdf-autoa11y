package net.boyechko.pdf.autoa11y;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TagSchema {
    Map<String, Rule> roles = new HashMap<>();
    
    public static final class Rule {
        String parentMustBe;
        Set<String> allowedChildren = Set.of();
        String childPattern;
        Integer minChildren;
        Integer maxChildren;
        Set<String> requiredChildren = Set.of();
    }
    
    public static TagSchema minimal() {
        TagSchema s = new TagSchema();
        Rule L = new Rule();
        L.allowedChildren = Set.of("LI");
        L.childPattern = "LI+";
        L.minChildren = 1;
        s.roles.put("L", L);

        Rule LI = new Rule();
        LI.parentMustBe = "L";
        LI.allowedChildren = Set.of("Lbl", "LBody");
        LI.childPattern = "Lbl* LBody";
        LI.minChildren = 1;
        LI.maxChildren = 2;
        s.roles.put("LI", LI);

        Rule Lbl = new Rule();
        Lbl.parentMustBe = "LI";
        s.roles.put("Lbl", Lbl);

        Rule LBody = new Rule();
        LBody.parentMustBe = "LI";
        s.roles.put("LBody", LBody);

        return s;
    }
}