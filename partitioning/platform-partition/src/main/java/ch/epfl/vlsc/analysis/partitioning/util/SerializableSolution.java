package ch.epfl.vlsc.analysis.partitioning.util;

public class SerializableSolution implements java.io.Serializable{
    public final SolutionIdentity id;
    public final int hashCode;
    public SerializableSolution(SolutionIdentity id, int hashCode) {
        this.id = id;
        this.hashCode = hashCode;
    }


    public SolutionIdentity getId(){
        return id;
    }

    public int getHashCode() {
        return hashCode;
    }
}