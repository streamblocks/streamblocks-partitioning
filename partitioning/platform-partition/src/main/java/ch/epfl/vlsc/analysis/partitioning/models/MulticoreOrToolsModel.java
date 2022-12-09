package ch.epfl.vlsc.analysis.partitioning.models;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;

import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import ch.epfl.vlsc.analysis.partitioning.models.PerformanceModel.Partition;
import ch.epfl.vlsc.analysis.partitioning.models.PerformanceModel.PartitioningSolution;
import ch.epfl.vlsc.analysis.partitioning.models.PerformanceModel.SoftwarePartition;

public class MulticoreOrToolsModel {

    private final Context context;
    private final CompilationTask task;
    private final MulticoreProfileDataBase multicoreDb;
    private final Double multicoreClockPeriod;
    private final Double timeLimit;
    private final ImmutableList<Instance> softwareActors;

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

        return task.getNetwork().getInstances().stream().filter(p -> p.getInstanceName().equals(name)).findFirst()
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

    public void restrictedGrowthConstraint(MPSolver solver, ImmutableList<SoftwarePartition> partitionList, Map<Instance, Map<SoftwarePartition, MPVariable>> instanceDecisionVariables, int numberOfCores) {

        Map<Instance, MPVariable> partitionNumbers = new HashMap<>();

        for (Instance inst : task.getNetwork().getInstances()) {

            MPVariable pNum = solver.makeIntVar(0, numberOfCores - 1, "pnum_" + inst.getInstanceName());
            MPConstraint pConstr = solver.makeConstraint(0, 0, "pnum_" + inst.getInstanceName() + "_constr");
            pConstr.setCoefficient(pNum, -1);
            for (SoftwarePartition p : partitionList) {
                pConstr.setCoefficient(instanceDecisionVariables.get(inst).get(p), p.toIndex());
            }
            // pNum = d_0_a * 0 + d_1_a * 1 + d_2_a * 2 + ... + d_n_a * n
            // therefore, pNum indicates the partition number that inst is assigned to
            partitionNumbers.put(inst, pNum);
        }

        // now that we have a mapping from instances to the partition numbers they may be assigned to, we can create the
        // symmetry breaking constraints.
        // 1. The first actors could be assigned to partition 0 or 1,
        // i.e., 0 <= pNum_0  <= 1
        // 2. pNum_j <= max(pNum_0, pNum_1, pNum_2, ..., pNum_(j-1)) + 1
        // i.e., actor j could either be assigned to a new "unused core" or assigned to an already used one.
        // For instance, if actor 0 is assigned to partition 0, then we can only assign actor 1 to partition 1
        // or 0, but not partition 2 and so on.

        // constraint for the first actor
        {
            Instance a0 = task.getNetwork().getInstances().get(0);
            MPConstraint c0 = solver.makeConstraint(0, 1, "restrictedGrowth_" + a0.getInstanceName());
            c0.setCoefficient(partitionNumbers.get(a0), 1);
        }

        for (int ix = 1; ix < task.getNetwork().getInstances().size(); ix ++) {

            Instance inst = task.getNetwork().getInstances().get(ix);
            ImmutableList.Builder<MPVariable> prevs = ImmutableList.builder();
            for (int kx = 0; kx < ix; kx++) {
                prevs.add(partitionNumbers.get(task.getNetwork().getInstances().get(kx)));
            }
            MPConstraint constr = solver.makeConstraint(0, 1, "restrictedGrowth_" + inst.getInstanceName());
            MPVariable prevMax = makeMax(solver, prevs.build(), 0, numberOfCores, "rgMax_" + inst.getInstanceName());
            constr.setCoefficient(partitionNumbers.get(inst), 1);
            constr.setCoefficient(prevMax, -1);

        }

        // lastly, we add a constraint such that all cores are used, this is simply
        // max(pNum_j) = numCores - 1

        MPVariable maxPNum = makeMax(solver, partitionNumbers.values().stream().collect(ImmutableList.collector()),
            0, numberOfCores, "max_pnum"
        );
        MPConstraint maxPNumIsNumCores = solver.makeConstraint(numberOfCores - 1, numberOfCores - 1);
        maxPNumIsNumCores.setCoefficient(maxPNum, 1);

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
                                                p -> solver.makeBoolVar(
                                                        "d_" + p.toIndex() + "^" + i.getInstanceName())))

                        ));
        // every actor is only mapped to a single partition, i.e., we have forall a:
        // \sum_p (d_p^a) = 1
        for (Instance inst : task.getNetwork().getInstances()) {
            MPConstraint constr = solver.makeConstraint(1, 1, inst.getInstanceName() + " unique partition");
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


        restrictedGrowthConstraint(solver, partitionList, instanceDecisionVariables, numberOfCores);

        MPVariable execTimeMax = makeMax(solver, partitionTime.values().stream().collect(ImmutableList.collector()), 0,
                upperBoundExecutionTime, "T_exec");
        MPVariable localTimeMax = makeMax(solver, localCommTime.values().stream().collect(ImmutableList.collector()), 0,
                upperBoundLocalCommTime, "T_intra");

        MPObjective objective = solver.objective();

        objective.setCoefficient(execTimeMax, 1);
        objective.setCoefficient(localTimeMax, 1);
        objective.setCoefficient(globalTime, 1);
        objective.setMinimization();
        solver.setTimeLimit(timeLimit.intValue() * 1000); // * 1000 because solver accepts timeout in ms
        dumpModel(solver, numberOfCores);
        context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, "Solving model for  " + numberOfCores + " cores"));
        final MPSolver.ResultStatus resultStatus = solver.solve();
        // solver.exportModelAsMpsFormat();
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {

            // collect the solution (sadly there is a single one with the MPSolver)
            context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO,
                    "The problem is " + resultStatus + " for " + numberOfCores + " cores"));
            ImmutableList.Builder<Partition<String>> pBuilder = ImmutableList.builder();
            Map<Instance, SoftwarePartition> collectedInstances = new HashMap<>();

            for (SoftwarePartition p : partitionList) {
                // collect all the actors that are in p
                ImmutableList.Builder<String> instancesInP = ImmutableList.builder();
                for (Instance inst : task.getNetwork().getInstances()) {

                    boolean assigned = instanceDecisionVariables.get(inst).get(p).solutionValue() == 1;
                    if (assigned) {
                        instancesInP.add(inst.getInstanceName());
                        if (collectedInstances.containsKey(inst)) {
                            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,
                                    String.format("%s is assigned to partitions %d and %d", inst.getInstanceName(),
                                            collectedInstances.get(inst).toIndex(), p.toIndex())));
                        }
                        collectedInstances.put(inst, p);
                    }

                }
                pBuilder.add(new Partition<>(instancesInP.build(), p));
            }
            return ImmutableList.of(new PartitioningSolution<>(pBuilder.build()));
        } else {
            throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR,
                    "Could not handle result status \"" + resultStatus.toString() + "\"!"));
        }

    }

    public void dumpModel(MPSolver solver, int numberOfCores) {

        Path dumpPath = context.getConfiguration().get(Compiler.targetPath).resolve("simple").resolve(String.valueOf(numberOfCores)).resolve("model.lp");
        context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO, "Dumping model to " + dumpPath));
        try {
            Files.createDirectories(dumpPath.getParent());
            PrintWriter writer = new PrintWriter(dumpPath.toFile());
            writer.print(solver.exportModelAsLpFormat());
            writer.close();
        } catch (FileNotFoundException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not open file to dump the model to " + dumpPath));
        } catch (IOException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not create directory to dump the model to " + dumpPath));
        }

    }

    public void dumpMulticoreConfig(
            String name,
            PartitioningSolution<String> partitionMap,
            MulticoreProfileDataBase multicoreDb) {

        File configFile = new File(name);

        try {
            // the config xml doc
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element configRoot = doc.createElement("configuration");
            doc.appendChild(configRoot);
            Element partitioningRoot = doc.createElement("partitioning");
            configRoot.appendChild(partitioningRoot);

            ImmutableList.Builder<String> availableInstancesBuilder = ImmutableList.builder();

            for (Partition<String> partition : partitionMap.getPartitions()) {
                ImmutableList<String> instances = partition.getInstances();
                if (!instances.isEmpty()) {
                    Element partitionRoot = doc.createElement("partition");

                    partitionRoot.setAttribute("id", String.valueOf(partition.getPartitionType().toIndex()));
                    instances.forEach(
                            instance -> {
                                Element instanceRoot = doc.createElement("instance");
                                instanceRoot.setAttribute("id", instance);
                                partitionRoot.appendChild(instanceRoot);
                            });
                    partitioningRoot.appendChild(partitionRoot);
                    availableInstancesBuilder.addAll(instances);
                }
            }

            Element connectionsRoot = doc.createElement("connections");

            ImmutableList<String> availableInstances = availableInstancesBuilder.build();

            Optional<String> virtualActor = Optional.of("system_plink_0");
            for (Connection connection : task.getNetwork().getConnections()) {

                String sourceInstance = connection
                        .getSource()
                        .getInstance()
                        .orElseThrow(
                                () -> new CompilationException(
                                        new Diagnostic(
                                                Diagnostic.Kind.ERROR,
                                                "The network contains a "
                                                        + "dangling connection "
                                                        + connection.toString())));
                String targetInstance = connection
                        .getTarget()
                        .getInstance()
                        .orElseThrow(
                                () -> new CompilationException(
                                        new Diagnostic(
                                                Diagnostic.Kind.ERROR,
                                                "The network contains a "
                                                        + "dangling connection "
                                                        + connection.toString())));

                String sourcePort = connection.getSource().getPort();
                String targetPort = connection.getTarget().getPort();
                Element fifoConnectionElem = doc.createElement("fifo-connection");
                String depth = String.valueOf(multicoreDb.getConnectionSettingsDataBase().get(connection).getDepth());
                if (availableInstances.contains(sourceInstance)
                        && availableInstances.contains(targetInstance)) {
                    fifoConnectionElem.setAttribute("source", sourceInstance);
                    fifoConnectionElem.setAttribute("target", targetInstance);
                    fifoConnectionElem.setAttribute("source-port", sourcePort);
                    fifoConnectionElem.setAttribute("target-port", targetPort);
                    fifoConnectionElem.setAttribute("size", depth);
                    connectionsRoot.appendChild(fifoConnectionElem);
                } else if (availableInstances.contains(sourceInstance)
                        && !availableInstances.contains(targetInstance)) {
                    fifoConnectionElem.setAttribute("source", sourceInstance);
                    fifoConnectionElem.setAttribute("target", virtualActor.get());
                    fifoConnectionElem.setAttribute("source-port", sourcePort);
                    fifoConnectionElem.setAttribute(
                            "target-port",
                            sourceInstance + "_" + sourcePort + "_" + targetInstance + "_" + targetPort);
                    fifoConnectionElem.setAttribute("size", depth);
                    connectionsRoot.appendChild(fifoConnectionElem);
                } else if (!availableInstances.contains(sourceInstance)
                        && availableInstances.contains(targetInstance)) {
                    fifoConnectionElem.setAttribute("source", virtualActor.get());
                    fifoConnectionElem.setAttribute("target", targetInstance);
                    fifoConnectionElem.setAttribute(
                            "source-port",
                            sourceInstance + "_" + sourcePort + "_" + targetInstance + "_" + targetPort);
                    fifoConnectionElem.setAttribute("target-port", targetPort);
                    fifoConnectionElem.setAttribute("size", depth);
                    connectionsRoot.appendChild(fifoConnectionElem);
                }
            }

            configRoot.appendChild(connectionsRoot);

            StreamResult configStream = new StreamResult(configFile);
            DOMSource configDom = new DOMSource(doc);
            Transformer configTransformer = TransformerFactory.newInstance().newTransformer();
            configTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            configTransformer.transform(configDom, configStream);

            context
                    .getReporter()
                    .report(
                            new Diagnostic(
                                    Diagnostic.Kind.INFO, "Config file saved to " + configFile.toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
