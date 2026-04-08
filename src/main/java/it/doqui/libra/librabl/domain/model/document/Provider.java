package it.doqui.libra.librabl.domain.model.document;

public enum Provider {
    UANATACA_BOX,
    UANATACA_CLOUD,
    GATEFIRE,
    PROXYSIGN;

    public static Provider correctedValueOf(String name) {
        if (name.startsWith("UANATACA")) {
            return Provider.UANATACA_BOX;
        }
        return Provider.valueOf(name);
    }

    public static String summarizedName(Provider provider) {
        if (provider.equals(Provider.UANATACA_BOX) || provider.equals(Provider.UANATACA_CLOUD)) {
            return "UANATACA";
        }
        return provider.name();
    }
}
