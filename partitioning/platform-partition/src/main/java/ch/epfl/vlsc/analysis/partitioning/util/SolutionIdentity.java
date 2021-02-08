package ch.epfl.vlsc.analysis.partitioning.util;

public class SolutionIdentity {
  public final int numberOfCores;
  public final int solutionNumber;

  public SolutionIdentity(int numberOfCores, int solutionNumber) {
    this.numberOfCores = numberOfCores;
    this.solutionNumber = solutionNumber;
  }
}
