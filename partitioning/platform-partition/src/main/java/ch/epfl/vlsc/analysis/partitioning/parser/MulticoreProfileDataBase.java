package ch.epfl.vlsc.analysis.partitioning.parser;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MulticoreProfileDataBase {

    private ExecutionProfileDataBase execDb;

    private TokenExchangeProfileDataBase tokenDb;

    private CommunicationProfileDataBase bwDb;

    private ConnectionSettingsDataBase auxDb;

    static public class ExecutionProfileDataBase extends ProfileDataBase<Instance, Long>{

        @Override
        String getObjectName(Instance instance) {
            return instance.getInstanceName();
        }
    }
    static public class TokenExchangeProfileDataBase extends ProfileDataBase<Connection, Long> {
        @Override
        String getObjectName(Connection connection) {
            return String.format("%s.%s->%s.%s tokens", connection.getSource().getInstance().orElse(""),
                    connection.getSource().getPort(),
                    connection.getTarget().getInstance().orElse(""), connection.getTarget().getPort());
        }
    }
    static public class CommunicationTicks{
        public enum Kind {
            LocalCore,
            Core2Core;
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
                case LocalCore: return this.intra;
                case Core2Core: return this.inter;
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
                    case LocalCore:
                        if (this.intra.isPresent())
                            throw new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            "LocalCore value already set in the builder"));
                        else
                            this.intra = Optional.of(value);
                        break;
                    case Core2Core:
                        if (this.inter.isPresent())
                            throw new CompilationException(
                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                            "Core2Core value already set in the builder"));
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

    static public class ConnectionSettings {
        private Integer depth;
        private Integer width;
        public ConnectionSettings(Integer depth, Integer width) {
            this.depth = depth;
            this.width = width;
        }

        public Integer getDepth() {
            return depth;
        }

        public Integer getWidth() {
            return width;
        }
    }
    static public class CommunicationProfileDataBase extends ProfileDataBase<Integer, CommunicationTicks>{

        @Override
        String getObjectName(Integer bufferSize) {
            return String.format("buffer size %d", bufferSize);
        }
    }

    static public class ConnectionSettingsDataBase extends  ProfileDataBase<Connection, ConnectionSettings> {
        @Override
        String getObjectName(Connection connection) {
            return String.format("%s.%s->%s.%s settings", connection.getSource().getInstance().orElse(""),
                    connection.getSource().getPort(),
                    connection.getTarget().getInstance().orElse(""), connection.getTarget().getPort());
        }
    }

    public MulticoreProfileDataBase() {
        this.execDb = new ExecutionProfileDataBase();
        this.bwDb = new CommunicationProfileDataBase();
        this.tokenDb = new TokenExchangeProfileDataBase();
        this.auxDb = new ConnectionSettingsDataBase();

    }

    public void setInstanceTicks(Instance instance, Long ticks) {
        execDb.set(instance, ticks);
    }
    public void setCommunicationTicks(Integer bufferSize, CommunicationTicks bw) {
        bwDb.set(bufferSize, bw);
    }
    public void setTokensExchanged(Connection connection, Long tokens) {
        this.tokenDb.set(connection, tokens);
    }

    public Long getInstanceTicks(Instance instance) {
        return execDb.get(instance);
    }

    /**
     * Computes the communication ticks for a given connection and its kind. Since we have a
     * database of communication ticks for limited number of buffer sizes (32, 64, ... bytes)
     * then if the connection has a custom size e.g. 3421 tokens capacity, we have to find
     * the nearest known buffer size ticks. Then we use compute how many full buffer transfers
     * are to be made based on the number tokens that are transferred over a fifo and we compute
     * the estimated communication ticks either LocalCore or Core2Core
     * @param connection the connection to compute its estimated ticks
     * @param kind the kind of the connection, LocalCore or Core2Core
     * @return Double value that estimates the communication ticks (not time) of the given connection
     */
    public Double getCommunicationTicks(Connection connection, CommunicationTicks.Kind kind) {
        Integer connectionBufferSizeBytes = this.getSettings(connection).getDepth() *
                this.getSettings(connection).getWidth();
        Integer nextProfiledBufferSizeBytes =
                (Integer.highestOneBit(connectionBufferSizeBytes) == connectionBufferSizeBytes) ?
                    connectionBufferSizeBytes :
                    Integer.highestOneBit(connectionBufferSizeBytes) << 1;
        // Number of ticks it takes to transfer nextProfiledBufferSizeBytes bytes over either
        // core to core or local core fifos
        CommunicationTicks ticksPerTx = this.bwDb.get(nextProfiledBufferSizeBytes);
        Long tokensExchanged = this.getTokensExchanged(connection);
        Double numTx = tokensExchanged.doubleValue() / nextProfiledBufferSizeBytes.doubleValue();
        return numTx * ticksPerTx.get(kind);

    }

    public CommunicationTicks getCommunicationTicks(Integer bufferSize) {
        return bwDb.get(bufferSize);
    }

    public Long getTokensExchanged(Connection connection) {
        return this.tokenDb.get(connection);
    }

    public void setSettings(Connection connection, ConnectionSettings settings) {
        this.auxDb.set(connection, settings);
    }
    public ConnectionSettings getSettings(Connection connection) {
        return auxDb.get(connection);
    }

    public TokenExchangeProfileDataBase getTokenExchangeProfileDataBase() {
        return tokenDb;
    }

    public ExecutionProfileDataBase getExecutionProfileDataBase() {
        return execDb;
    }

    public CommunicationProfileDataBase getCommunicationProfileDataBase() {
        return bwDb;
    }

    public ConnectionSettingsDataBase getConnectionSettingsDataBase() {
        return auxDb;
    }
}
