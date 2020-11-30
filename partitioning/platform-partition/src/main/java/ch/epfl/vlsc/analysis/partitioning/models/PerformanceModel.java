package ch.epfl.vlsc.analysis.partitioning.models;

import ch.epfl.vlsc.analysis.partitioning.parser.CommonProfileDataBase;
import ch.epfl.vlsc.analysis.partitioning.parser.MulticoreProfileDataBase;
import gurobi.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import se.lth.cs.tycho.compiler.CompilationTask;

import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;



import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.lang.reflect.Type;
import java.util.*;

public abstract class PerformanceModel {


    protected Context context;
    protected CompilationTask task;
    GRBModel model;


    public class HardwarePartition implements TypedPartition {

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

    public class SoftwarePartition implements TypedPartition {
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

    public class Partition<T> {

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

    public class PartitioningSolution<T> {
        private final ImmutableList<Partition<T>> partitions;
        public PartitioningSolution(ImmutableList<Partition<T>> partitions) {
            this.partitions = partitions;
        }
        public ImmutableList<Partition<T>> getPartitions() {
            return this.partitions;
        }
    }


    protected class DecisionVariables {

        // -- map from partition to the corresponding decision variable
        private final Map<TypedPartition, GRBVar> vars;
        // -- the partition number variable which is part of the restricted growth string
        private final GRBVar partitionNumber;
        protected DecisionVariables (Instance instance,
                                     List<TypedPartition> partitions,
                                     GRBModel model) {
            this.vars = new HashMap<>();
            int numberOfCores=  partitions.size();

            try {

                partitionNumber = model.addVar(0.0, numberOfCores - 1, 0.0,
                        GRB.INTEGER, "a_" + instance.getInstanceName());

                for (TypedPartition p : partitions) {

                    GRBVar decisionInstancePartition = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            String.format("d_%s_%s", instance.getInstanceName(), p.toString()));
                    vars.put(p, decisionInstancePartition);
                }

            } catch (GRBException e) {
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "Could not build the decision variables for instance " + instance.getInstanceName()));

            }
        }

        /**
         * Builds and expression that can be used to constraint and instance to exactly one partition
         * @return A linear expression that represents the number of times an instance has been assigned
         * to a partition, to make sure each instance is assigned to exactly one partition, the returned expression
         * should be constrained to the value 1.0
         */
        public GRBLinExpr getUniquePartitionConstraint() {
            try {
                GRBLinExpr uniqueDecisionConstraint = new GRBLinExpr();
                GRBVar[] decisionVars = vars.values().toArray(new GRBVar[vars.values().size()]);
                double[] coeffs = new double [vars.values().size()];
                Arrays.fill(coeffs, 1.0);
                uniqueDecisionConstraint.addTerms(coeffs, decisionVars);
                return uniqueDecisionConstraint;

            } catch (GRBException e) {
                error("Could not create unique decision constraint expression");
            }
            return new GRBLinExpr();
        }

        public GRBVar getPartitionNumber() {
            return this.partitionNumber;
        }
        public GRBVar getDecisionVariable(TypedPartition p) {
            return this.vars.get(p);
        }

        public Set<TypedPartition> getPartitionSet() {
            return vars.keySet();
        }
    }


    protected Instance findInstance(Connection.End end) {

        String instanceName = end.getInstance().orElseThrow(
                () -> new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "Could not get instance from " +
                                        "connection end None." + end.getPort())));
        return task.getNetwork().getInstances().stream()
                .filter(
                        instance ->
                                instance.getInstanceName().equals(instanceName)
                ).findFirst().orElseThrow(
                        () -> new CompilationException(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                        "Could not find instance " + instanceName)));
    }
    protected String getConnectionName(Connection c) {

        return String.format("{%s.%s->%s.%s}",
                c.getSource().getInstance().orElse(""), c.getSource().getPort(),
                c.getTarget().getInstance().orElse(""), c.getTarget().getPort());

    }

    protected void info(String message) {
        context.getReporter().report(
                new Diagnostic(Diagnostic.Kind.INFO, message)
        );
    }
    protected void error(String message) {
        context.getReporter().report(
                new Diagnostic(Diagnostic.Kind.ERROR, message)
        );
    }
    protected void fatalError(String message) {
        throw new CompilationException(new Diagnostic(
                Diagnostic.Kind.ERROR, message
        ));
    }


    public void dumpMulticoreConfig(String name, PartitioningSolution<String> partitionMap) {


        File configFile = new File(name);


        try {
            // the config xml doc
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element configRoot = doc.createElement("configuration");
            doc.appendChild(configRoot);
            Element partitioningRoot = doc.createElement("partitioning");
            configRoot.appendChild(partitioningRoot);


            for (Partition<String> partition: partitionMap.getPartitions()) {
                ImmutableList<String> instances = partition.getInstances();
                if (!instances.isEmpty()) {

                    Element partitionRoot = doc.createElement("partition");

                    partitionRoot.setAttribute("id", String.valueOf(partition.getPartitionType().toIndex()));
                    instances.forEach( instance -> {
                        Element instanceRoot = doc.createElement("instance");
                        instanceRoot.setAttribute("id", instance);
                        partitionRoot.appendChild(instanceRoot);
                    });
                    partitioningRoot.appendChild(partitionRoot);
                }
            }

            StreamResult configStream = new StreamResult(configFile);
            DOMSource configDom = new DOMSource(doc);
            Transformer configTransformer = TransformerFactory.newInstance().newTransformer();
            configTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            configTransformer.transform(configDom, configStream);

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Config file saved to " + configFile.toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void dumpXcfConfig(String name, PartitioningSolution<Instance> partitionMap) {

        File xcfFile = new File(name);
        try {



        } catch (Exception e) {
            fatalError("Could not dump xcf to " + xcfFile.toString() + ": " + e.getMessage());
        }
    }
}
