package ch.epfl.vlsc.analysis.partitioning.parser;


import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

public class DeviceProfileDataBase extends CommonProfileDataBase{


    /**
     * Return the time connection requires to transfer all tokens, the time is absolute time not ticks
     * @param connection
     * @param kind
     * @return
     */
    public Double getCommunicationTime(Connection connection, CommunicationTicks.Kind kind) {
        throw new CompilationException(
                new Diagnostic(
                        Diagnostic.Kind.ERROR, "unimplemented method"));

    }
}
