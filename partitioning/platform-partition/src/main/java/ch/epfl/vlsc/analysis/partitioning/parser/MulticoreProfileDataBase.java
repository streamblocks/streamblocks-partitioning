package ch.epfl.vlsc.analysis.partitioning.parser;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MulticoreProfileDataBase extends CommonProfileDataBase{



    private TokenExchangeProfileDataBase tokenDb;



    private ConnectionSettingsDataBase auxDb;


    static public class TokenExchangeProfileDataBase extends ProfileDataBase<Connection, Long> {
        @Override
        String getObjectName(Connection connection) {
            return String.format("%s.%s->%s.%s tokens", connection.getSource().getInstance().orElse(""),
                    connection.getSource().getPort(),
                    connection.getTarget().getInstance().orElse(""), connection.getTarget().getPort());
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

    static public class ConnectionSettingsDataBase extends  ProfileDataBase<Connection, ConnectionSettings> {
        @Override
        String getObjectName(Connection connection) {
            return String.format("%s.%s->%s.%s settings", connection.getSource().getInstance().orElse(""),
                    connection.getSource().getPort(),
                    connection.getTarget().getInstance().orElse(""), connection.getTarget().getPort());
        }
    }

    public MulticoreProfileDataBase() {
        this.execDb = new CommonProfileDataBase.ExecutionProfileDataBase();
        this.bwDb = new CommonProfileDataBase.CommunicationProfileDataBase();
        this.tokenDb = new TokenExchangeProfileDataBase();
        this.auxDb = new ConnectionSettingsDataBase();

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
    public Double getCommunicationTicks(Connection connection, CommonProfileDataBase.CommunicationTicks.Kind kind) {
        Integer connectionBufferSizeBytes = this.getSettings(connection).getDepth() *
                this.getSettings(connection).getWidth();
        Integer nextProfiledBufferSizeBytes =
                (Integer.highestOneBit(connectionBufferSizeBytes) == connectionBufferSizeBytes) ?
                    connectionBufferSizeBytes :
                    Integer.highestOneBit(connectionBufferSizeBytes) << 1;
        // Number of ticks it takes to transfer nextProfiledBufferSizeBytes bytes over either
        // core to core or local core fifos
        if (nextProfiledBufferSizeBytes < getMinimumProfiledBufferSize()) {
            nextProfiledBufferSizeBytes = getMinimumProfiledBufferSize();
        }
        try {
            CommonProfileDataBase.CommunicationTicks ticksPerTx = getCommunicationTicks(nextProfiledBufferSizeBytes);
            Long tokensExchanged = this.getTokensExchanged(connection);
            Double numTx = tokensExchanged.doubleValue() / nextProfiledBufferSizeBytes.doubleValue();
            return numTx * ticksPerTx.get(kind);

        } catch (CompilationException e) {

            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR,
                            String.format("Could not find communication ticks for connection " +
                                    "%s.%s-->%s.%s with depth %d and width %d, closest power of 2 buffer size is %s\n" +
                                            "Try running the multicore bandwidth profiler for a wider range of buffer sizes",
                                    connection.getSource().getInstance().orElse(""),
                                    connection.getSource().getPort(),
                                    connection.getTarget().getInstance().orElse(""),
                                    connection.getTarget().getPort(),
                                    this.getSettings(connection).getDepth(),
                                    this.getSettings(connection).getWidth(),
                                    nextProfiledBufferSizeBytes)));
        }
    }

    public Double getCommunicationTicks(Connection connection, Integer bufferSize,
                                        CommonProfileDataBase.CommunicationTicks.Kind kind) {
        CommonProfileDataBase.CommunicationTicks ticksPerTx = getCommunicationTicks(bufferSize);
        Long tokensExchanged = this.getTokensExchanged(connection);
        Double numTx = tokensExchanged.doubleValue() / bufferSize.doubleValue();
        return numTx * ticksPerTx.get(kind);
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

    public ConnectionSettingsDataBase getConnectionSettingsDataBase() {
        return auxDb;
    }

    public Integer getMinimumProfiledBufferSize() {
        return Collections.min(this.bwDb.keySet());
    }
}
