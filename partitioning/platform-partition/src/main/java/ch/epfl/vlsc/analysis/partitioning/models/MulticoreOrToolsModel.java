package ch.epfl.vlsc.analysis.partitioning.models;

import se.lth.cs.tycho.compiler.CompilationTask;
import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;

import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

public class MulticoreOrToolsModel {

    private final Context context;
    private final CompilationTask task;
    private final MulticoreProfileDataBase multicoreDb;
    private final Double multicoreClockPeriod;
    private final Double timeLimit;
    private final ImmutableList<Instance> softwareActors;

    public static class HardwarePartition implements TypedPartition {

        private final int index;

        public HardwarePartition(int i) {
            this.index = i;
        }

        @Override
        public int toIndex() {
            return this.index;
        }

        @Override
        public String toString() {
            return "accel";
        }
    }

    public static class SoftwarePartition implements TypedPartition {
        private final int index;

        public SoftwarePartition(int index) {
            this.index = index;
        }

        @Override
        public int toIndex() {
            return this.index;
        }

        @Override
        public String toString() {
            return "core_" + this.index;
        }
    }

    public abstract interface TypedPartition {
        public abstract int toIndex();

        public abstract String toString();
    }

    public static class Partition<T> {

        private final ImmutableList<T> instances;
        private final TypedPartition ptype;

        public Partition(ImmutableList<T> instances, TypedPartition p) {
            this.instances = instances;
            this.ptype = p;
        }

        public ImmutableList<T> getInstances() {
            return this.instances;
        }

        public TypedPartition getPartitionType() {
            return this.ptype;
        }
    }

    public static class PartitioningSolution<T> {
        private final ImmutableList<Partition<T>> partitions;

        public PartitioningSolution(ImmutableList<Partition<T>> partitions) {
            this.partitions = partitions;
        }

        public ImmutableList<Partition<T>> getPartitions() {
            return this.partitions;
        }
    }

    public MulticoreOrToolsModel(
            CompilationTask task,
            Context context,
            MulticoreProfileDataBase multicoreDb,
            Double multicoreClockPeriod,
            Double timeLimit) {

        this.multicoreDb = multicoreDb;
        this.task = task;
        this.context = context;
        this.multicoreClockPeriod = multicoreClockPeriod;
        this.timeLimit = timeLimit;
        this.softwareActors = task.getNetwork().getInstances();

    }

    private ImmutableList<SoftwarePartition> makePartitionList(int numberOfCores) {
        ImmutableList.Builder<SoftwarePartition> builder = ImmutableList.builder();
        for (int i = 0; i < numberOfCores; i++) {
            builder.add(new SoftwarePartition(i));
        }
        return builder.build();
    }

    private Instance getInstance(Connection.End end) {

        String name = end.getInstance().orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Could not find instance in connection")));
        return task.getNetwork().getInstances().stream().filter(p -> p.getInstanceName() == name).findFirst()
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(
                                        Diagnostic.Kind.ERROR, "Could not find instance " + name)));
    }

    private MPVariable makeConjunction(MPSolver solver, MPVariable x1, MPVariable x2) {
        // y = x1 & x2 can be represented as:
        // 0 <= y <= 1
        // c1: y >= x1 + x2 - 1, i.e., x1 + x2 - y <= 1
        // c2: y <= x1
        // c3: y <= x2
        MPVariable y = solver.makeBoolVar("conj");
        MPConstraint c1 = solver.makeConstraint(Double.NEGATIVE_INFINITY, 1.0); // x1 + x2 - y <= 1
        c1.setCoefficient(x1, 1);
        c1.setCoefficient(x2, 1);
        c1.setCoefficient(y, -1);
        MPConstraint c2 = solver.makeConstraint(0.0, Double.POSITIVE_INFINITY); // 0 <= x1 - y
        c2.setCoefficient(x1, 1);
        c2.setCoefficient(y, -1);
        MPConstraint c3 = solver.makeConstraint(0, Double.POSITIVE_INFINITY); // 0 <= x2 - y
        c3.setCoefficient(x2, 1);
        c3.setCoefficient(y, -1);

        return y;
    }

    private MPVariable makeDisjunction(MPSolver solver, MPVariable x1, MPVariable x2) {

        // y = x | x2 can be represented as
        // 0 <= y <= 1
        // c1: y <= x1 + x2
        // c2: y >= x1
        // c2: y >= x2
        MPVariable y = solver.makeBoolVar("disjunct");
        MPConstraint c1 = solver.makeConstraint(0, Double.POSITIVE_INFINITY);
        c1.setCoefficient(y, -1);
        c1.setCoefficient(x1, 1);
        c1.setCoefficient(x2, 1);

        MPConstraint c2 = solver.makeConstraint(0, Double.POSITIVE_INFINITY);
        c2.setCoefficient(y, 1);
        c2.setCoefficient(x1, -1);

        MPConstraint c3 = solver.makeConstraint(0, Double.POSITIVE_INFINITY);
        c3.setCoefficient(y, 1);
        c3.setCoefficient(x2, -1);

        return y;

    }

    private MPVariable makeMax(MPSolver solver, ImmutableList<MPVariable> args, double lb, double ub, String name) {

        MPVariable m = solver.makeNumVar(lb, ub, name);
        MPVariable[] b = solver.makeBoolVarArray(args.size());
        for (int ix = 0; ix < args.size(); ix++) {
            MPConstraint greater = solver.makeConstraint(0, Double.POSITIVE_INFINITY); // 0 <= m - args[ix]
            greater.setCoefficient(m, 1);
            greater.setCoefficient(args.get(ix), -1);
            MPConstraint less = solver.makeConstraint(Double.NEGATIVE_INFINITY, ub); // 0 <= args[ix] + (1 - b[ix]) * ub
                                                                                     // - m
            // which is m + b[ix] * ub - argx[ix] <= ub
            less.setCoefficient(m, 1);
            less.setCoefficient(b[ix], ub);
            less.setCoefficient(args.get(ix), -1);

        }

        return m;
    }

    public ImmutableList<PartitioningSolution<String>> solveModel(int numberOfCores) {
        Loader.loadNativeLibraries();
        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not initialize SCIP solver"));
        }

        ImmutableList<SoftwarePartition> partitionList = makePartitionList(numberOfCores);

        // For every partition p and actor a create a decision variable d_p^a
        Map<Instance, Map<SoftwarePartition, MPVariable>> instanceDecisionVariables = task.getNetwork().getInstances()
                .stream()
                .collect(
                        Collectors.toMap(Function.identity(),
                                i -> partitionList.stream().collect(
                                        Collectors.toMap(
                                                Function.identity(),
                                                p -> solver.makeBoolVar("d_" + p.index + "^" + i.getInstanceName())))

                        ));
        // every actor is only mapped to a single partition, i.e., we have forall a:
        // \sum_p (d_p^a) = 1
        for (Instance inst : task.getNetwork().getInstances()) {
            MPConstraint constr = solver.makeConstraint(0, 1, inst.getInstanceName() + " unique partition");
            // zero out every coeff (this may not be necessary but the examples in or-tools
            // explicitly sets the coefficient of all variables)
            // instanceDecisionVariables.values().forEach(l -> l.values().forEach(d ->
            // constr.setCoefficient(d, 0)));
            // then set the relevant ones to 1
            for (SoftwarePartition p : partitionList) {
                constr.setCoefficient(instanceDecisionVariables.get(inst).get(p), 1);
            }
        }

        // the upper bound execution time (excluding communication is the simply the sum
        // of all actor ticks, scaled by the clock)
        Double upperBoundExecutionTime = task
                .getNetwork().getInstances().stream().map(multicoreDb::getInstanceTicks).reduce(Long::sum).orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(
                                        Diagnostic.Kind.ERROR,
                                        "Could not" + " compute partition execution time upper bound")))
                .doubleValue()
                * this.multicoreClockPeriod;
        // set the computation time of each partition
        Map<SoftwarePartition, MPVariable> partitionTime = partitionList.stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        p -> solver.makeNumVar(0, upperBoundExecutionTime, "T_exec_" + p.toString())));
        for (SoftwarePartition partition : partitionList) {
            // a constraint to set T_exec_p = \sum_a(d_p^a * exec(a))
            MPConstraint constr = solver.makeConstraint(0.0, 0.0);
            constr.setCoefficient(partitionTime.get(partition), -1);
            for (Instance instance : task.getNetwork().getInstances()) {
                MPVariable dpa = instanceDecisionVariables.get(instance).get(partition);
                constr.setCoefficient(dpa, multicoreDb.getInstanceTicks(instance).doubleValue() * multicoreClockPeriod);
            }
        }

        // upper bound for local and global communication time
        Double upperBoundLocalCommTime = task.getNetwork().getConnections().stream()
                .map(
                        con -> multicoreDb.getCommunicationTicks(
                                con, CommonProfileDataBase.CommunicationTicks.Kind.Local)
                                * multicoreClockPeriod)
                .reduce(Double::sum)
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(
                                        Diagnostic.Kind.ERROR,
                                        "Could not compute local communication time upper bound")));

        Double upperBoundGlobalCommTime = task.getNetwork().getConnections().stream()
                .map(
                        con -> multicoreDb.getCommunicationTicks(
                                con, CommonProfileDataBase.CommunicationTicks.Kind.Global)
                                * multicoreClockPeriod)
                .reduce(Double::sum)
                .orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(
                                        Diagnostic.Kind.ERROR,
                                        "Could not compute global communication time upper bound")));

        // set the local communication time of each partition
        Map<SoftwarePartition, MPVariable> localCommTime = partitionList.stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        p -> solver.makeNumVar(0.0, numberOfCores, "T_lc_" + p.toString())));

        for (SoftwarePartition partition : partitionList) {

            MPConstraint constr = solver.makeConstraint(0.0, 0.0);
            constr.setCoefficient(localCommTime.get(partition), -1);
            for (Connection connection : task.getNetwork().getConnections()) {

                Instance sourceInst = getInstance(connection.getSource());
                Instance targetInst = getInstance(connection.getTarget());
                MPVariable sourceD = instanceDecisionVariables.get(sourceInst).get(partition);
                MPVariable targetD = instanceDecisionVariables.get(targetInst).get(partition);
                MPVariable onTheSamePartition = makeConjunction(solver, sourceD, targetD);
                double commT = this.multicoreDb.getCommunicationTicks(
                        connection, CommonProfileDataBase.CommunicationTicks.Kind.Local)
                        * this.multicoreClockPeriod;
                constr.setCoefficient(onTheSamePartition, commT);
            }
        }



        MPVariable globalTime = solver.makeNumVar(0.0, upperBoundGlobalCommTime, "T_inter");
        MPConstraint gTimeConstr = solver.makeConstraint(0.0, 0.0); // T_inter = sum(..), i.e., sum(..) - T_iter = 0
        gTimeConstr.setCoefficient(globalTime, -1);
        for (Connection connection : task.getNetwork().getConnections()) {

            for (SoftwarePartition p : partitionList) {

                for (SoftwarePartition q : partitionList) {
                    if (p.toIndex() != q.toIndex()) {
                        Instance sourceInst = getInstance(connection.getSource());
                        Instance targetInst = getInstance(connection.getTarget());
                        MPVariable sourceDp = instanceDecisionVariables.get(sourceInst).get(p);
                        MPVariable sourceDq = instanceDecisionVariables.get(sourceInst).get(q);
                        MPVariable targetDp = instanceDecisionVariables.get(targetInst).get(p);
                        MPVariable targetDq = instanceDecisionVariables.get(targetInst).get(q);

                        MPVariable dSourcePTargetQ = makeConjunction(solver, sourceDp, targetDq);
                        MPVariable dSourceQTargetP = makeConjunction(solver, sourceDq, targetDp);
                        MPVariable disjunction = makeDisjunction(solver, dSourcePTargetQ, dSourceQTargetP);
                        double commT = this.multicoreDb.getCommunicationTicks(
                                connection, CommonProfileDataBase.CommunicationTicks.Kind.Global)
                                * this.multicoreClockPeriod;
                        gTimeConstr.setCoefficient(disjunction, commT);

                    }

                }
            }

        }

        MPVariable execTimeMax = makeMax(solver, partitionTime.values().stream().collect(ImmutableList.collector()), 0, upperBoundExecutionTime, "T_exec");
        MPVariable localTimeMax = makeMax(solver, localCommTime.values().stream().collect(ImmutableList.collector()), 0,
                upperBoundLocalCommTime, "T_intra");

        MPObjective objective = solver.objective();

        objective.setCoefficient(execTimeMax, 1);
        objective.setCoefficient(localTimeMax, 1);
        objective.setCoefficient(globalTime, 1);
        objective.setMinimization();
        solver.setTimeLimit(10 * 60 * 1000); // 10 minutes

        final MPSolver.ResultStatus resultStatus = solver.solve();
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL) {

        } else if (resultStatus == MPSolver.ResultStatus.FEASIBLE) {

        } else {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Could not handle result status \"" + resultStatus.toString() + "\"!" ));
        }

        return ImmutableList.empty();
    }


    // private PartitioningSolution<String>

}
