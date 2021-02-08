package ch.epfl.vlsc.analysis.partitioning.util;

public class ProfileData {
    public Double multiplier;
    public Double freq;
    public String path;

    public ProfileData() {}

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public Double getMultiplier() {
        return this.multiplier;
    }

    public void setFreq(Double freq) {
        this.freq = freq;
    }

    public Double getFreq() {
        return this.freq;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }
}