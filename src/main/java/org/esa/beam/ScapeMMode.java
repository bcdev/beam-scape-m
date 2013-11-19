package org.esa.beam;

/**
 * Scepe-M processing mode enumeration
 *
 * @author Tonio Fincke, Olaf Danne
 */
public enum ScapeMMode {
    SHORT("SHORT"),
    MEDIUM("MEDIUM"),
    LONG("LONG");

    private final String label;

    private ScapeMMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
