package ch.epfl.vlsc.analysis.partitioning.models;

import gurobi.GRBModel;
import se.lth.cs.tycho.compiler.CompilationTask;

import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

public abstract class PerformanceModel {


    protected Context context;
    protected CompilationTask task;
    GRBModel model;


    protected class HardwarePartition extends TypedPartition {

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

    protected class SoftwarePartition extends TypedPartition {
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

    protected abstract class TypedPartition {
        public abstract int toIndex();
        public abstract String toString();
    }


    /**TODO: handle potential errors*/
    protected Instance findSourceInstance(Connection.End source) {

        return task.getNetwork().getInstances().stream()
                .filter(
                        instance ->
                                instance.getInstanceName().equals(source.getInstance().get())
                ).findFirst().get();

    }
    /**TODO: handle potential errors*/
    protected Instance findTargetInstance(Connection.End target) {
        return task.getNetwork().getInstances().stream()
                .filter(
                        instance ->
                                instance.getInstanceName().equals(target.getInstance().get())

                ).findFirst().get();
    }

    protected Instance findInstance(Connection.End end) {
        return task.getNetwork().getInstances().stream()
                .filter(
                        instance ->
                                instance.getInstanceName().equals(end.getInstance().get())

                ).findFirst().get();
    }
    protected String getConnectionName(Connection c) {

        return String.format("{%s.%s->%s.%s}",
                c.getSource().getInstance().orElse(""), c.getSource().getPort(),
                c.getTarget().getInstance().orElse(""), c.getTarget().getPort());

    }
}
