package net.boyechko.pdf.autoa11y;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TagSchema {
    public Map<String, Rule> roles;

    public static final class Rule {
        String parent_must_be;
        Set<String> allowed_children;
        Set<String> required_children;
        Integer min_children;
        Integer max_children;
        String child_pattern;

        public String getParentMustBe() { return parent_must_be; }
        public Set<String> getAllowedChildren() { return allowed_children; }
        public Set<String> getRequiredChildren() { return required_children; }
        public Integer getMinChildren() { return min_children; }
        public Integer getMaxChildren() { return max_children; }
        public String getChildPattern() { return child_pattern; }
    }

    public TagSchema() {
        this.roles = new HashMap<>();
    }

    public Map<String, Rule> getRoles() {
        return roles;
    }
    
    public static TagSchema minimal() {
        TagSchema s = new TagSchema();

        Rule L = new Rule();
        L.allowed_children = Set.of("LI");
        L.min_children = 1;
        s.roles.put("L", L);

        Rule LI = new Rule();
        LI.parent_must_be = "L";
        LI.allowed_children = Set.of("Lbl", "LBody");
        LI.min_children = 1;
        LI.max_children = 2;
        s.roles.put("LI", LI);

        Rule Lbl = new Rule();
        s.roles.put("Lbl", Lbl);

        Rule LBody = new Rule();
        LBody.parent_must_be = "LI";
        s.roles.put("LBody", LBody);

        return s;
    }
}
