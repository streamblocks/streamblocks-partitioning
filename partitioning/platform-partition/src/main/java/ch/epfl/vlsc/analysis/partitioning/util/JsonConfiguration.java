package ch.epfl.vlsc.analysis.partitioning.util;

public class JsonConfiguration {
    public String name;
    public String comment;
    public int cores;
    public ProfileData systemc;
    public ProfileData opencl;
    public ProfileData bandwidth;
    public ProfileData software;
    public PartitionSettings.Mode mode;

    public JsonConfiguration() {}

    public ProfileData getBandwidth() {
        return bandwidth;
    }

    public ProfileData getOpencl() {
        return opencl;
    }

    public ProfileData getSoftware() {
        return software;
    }

    public ProfileData getSystemc() {
        return systemc;
    }

    public String getComment() {
        return comment;
    }

    public String getName() {
        return name;
    }

    public void setBandwidth(ProfileData bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOpencl(ProfileData opencl) {
        this.opencl = opencl;
    }

    public void setSoftware(ProfileData software) {
        this.software = software;
    }

    public void setSystemc(ProfileData systemc) {
        this.systemc = systemc;
    }

    public void setMode(PartitionSettings.Mode mode) {
        this.mode = mode;
    }

    public PartitionSettings.Mode getMode() {
        return mode;
    }

    public int getCores() {
        return cores;
    }
    public void setCores(int cores) {
        this.cores = cores;
    }
}