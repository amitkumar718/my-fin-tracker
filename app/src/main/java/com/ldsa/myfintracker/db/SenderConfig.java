package com.ldsa.myfintracker.db;

import java.util.regex.Pattern;

public class SenderConfig {
    public long id;
    public String pattern;
    public String displayName;
    public boolean isRegex;
    public String amountRegex;
    public String dateRegex;
    public String merchantRegex;
    public String cardRegex;

    public boolean matches(String address) {
        if (address == null || pattern == null) return false;
        if (isRegex) {
            try {
                return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(address).find();
            } catch (Exception e) {
                return false;
            }
        }
        return address.equalsIgnoreCase(pattern);
    }

    public String getLabel() {
        return (displayName != null && !displayName.isEmpty()) ? displayName : pattern;
    }
}
