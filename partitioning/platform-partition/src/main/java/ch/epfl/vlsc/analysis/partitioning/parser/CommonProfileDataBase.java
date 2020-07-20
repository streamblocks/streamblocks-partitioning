package ch.epfl.vlsc.analysis.partitioning.parser;

import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.Optional;

public class CommonProfileDataBase {

    protected ExecutionProfileDataBase execDb;

    protected CommunicationProfileDataBase bwDb;


    static public class CommunicationTicks{
        public enum Kind {
            Local,
            Global;
        };
        public CommunicationTicks(Long intra, Long inter) {
            this.inter = inter;
            this.intra = intra;
        }
        static public Builder builder() {
            return new Builder();
        }
        private Long intra;
        private Long inter;
        public Long get(Kind kind) {
            switch (kind) {
                case Local: return this.intra;
                case Global: return this.inter;
                default: throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR, "invalid bandwidth kind " + kind.toString()));
            }
        }

        static public class Builder {
            Optional<Long> inter;
            Optional<Long> intra;
            public Builder() {
                this.inter = Optional.empty();
                this.intra = Optional.empty();
            }
            public void set(Long value, Kind kind) {
                switch (kind) {
                    case Local:
                        if (this.intra.isPresent())
                            throw new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            "local value already set in the builder"));
                        else
                            this.intra = Optional.of(value);
                        break;
                    case Global:
                        if (this.inter.isPresent())
                            throw new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            "external value already set in the builder"));
                        else
                            this.inter = Optional.of(value);
                        break;
                    default: throw new CompilationException(
                            new Diagnostic(Diagnostic.Kind.ERROR, "invalid bandwidth kind " + kind.toString()));
                }
            }
            public CommunicationTicks build() {
                if (this.inter.isPresent() && this.intra.isPresent()) {
                    return new CommunicationTicks(this.intra.get(), this.inter.get());
                } else {
                    throw new CompilationException(
                            new Diagnostic(Diagnostic.Kind.ERROR, "trying to build an " +
                                    "incomplete CommunicationBandwidth"));
                }
            }
        }
    }
    static public class ExecutionProfileDataBase extends ProfileDataBase<Instance, Long>{

        @Override
        String getObjectName(Instance instance) {
            return instance.getInstanceName();
        }
    }
    static public class CommunicationProfileDataBase extends ProfileDataBase<Integer, CommunicationTicks>{

        @Override
        String getObjectName(Integer bufferSize) {
            return String.format("buffer size %d", bufferSize);
        }
    }

    public void setInstanceTicks(Instance instance, Long ticks) {
        execDb.set(instance, ticks);
    }
    public void setCommunicationTicks(Integer bufferSize, CommonProfileDataBase.CommunicationTicks bw) {
        bwDb.set(bufferSize, bw);
    }
    public Long getInstanceTicks(Instance instance) {
        return execDb.get(instance);
    }
    public CommunicationTicks getCommunicationTicks(Integer bufferSize) {
        return bwDb.get(bufferSize);
    }


    public ExecutionProfileDataBase getExecutionProfileDataBase() {
        return execDb;
    }

    public CommunicationProfileDataBase getCommunicationProfileDataBase() {
        return bwDb;
    }
}
