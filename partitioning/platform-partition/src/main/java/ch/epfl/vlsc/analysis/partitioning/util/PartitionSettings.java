package ch.epfl.vlsc.analysis.partitioning.util;

import se.lth.cs.tycho.settings.*;

import java.nio.file.Path;

public class PartitionSettings {

    public static PathSetting multiCoreProfilePath = new PathSetting() {
        @Override
        public String getKey() {
            return "multicore-profile-path";
        }

        @Override
        public String getDescription() {
            return "path to the profile xml file generated by the multicore backend with " +
                    "--with-complexity and --with-bandwidth flags";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static PathSetting systemCProfilePath = new PathSetting() {
        @Override
        public String getKey() {
            return "systemc-profile-path";
        }

        @Override
        public String getDescription() {
            return "path to the profile xml file generated by the VivadoHLS systemc backend";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static PathSetting openCLProfilePath = new PathSetting() {
        @Override
        public String getKey() {
            return "ocl-profile-path";
        }

        @Override
        public String getDescription() {
            return "path to the profiling info generated by the opencl loopback tests";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static PathSetting multicoreCommunicationProfilePath = new PathSetting() {
        @Override
        public String getKey() {
            return "core-communication-profile-path";
        }

        @Override
        public String getDescription() {
            return "path to the profiling files generated by the multicore bandwidth tests";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static PathSetting configPath = new PathSetting() {
        @Override
        public String getKey() {
            return "config-path";
        }

        @Override
        public String getDescription() {
            return "path to generate a config xml file for the multicore backend";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };

    public static IntegerSetting cpuCoreCount = new IntegerSetting() {
        @Override
        public String getKey() {
            return "num-cores";
        }

        @Override
        public String getDescription() {
            return "Number of available CPU cores, will translate into number of" +
                    "pthreads on the multicore backend";
        }

        @Override
        public Integer defaultValue(Configuration configuration) {
            return 1;
        }
    };


    public enum Mode {
        HOMOGENEOUS,
        HETEROGENEOUS,
        PINNED_HETEROGENEOUS

    }


    public static EnumSetting<Mode> searchMode = new EnumSetting<Mode>(Mode.class) {
        @Override
        public String getKey() {
            return "search-mode";
        }

        @Override
        public String getDescription() {
            return "Type of the search to be performed, homogenous multicore search, " +
                    "heterogeneous search, or pinned heterogeneous search";
        }

        @Override
        public Mode defaultValue(Configuration configuration) {
            return Mode.HOMOGENEOUS;
        }

    };

}