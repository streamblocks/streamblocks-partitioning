package ch.epfl.vlsc.analysis.partitioning.util;

import com.google.gson.annotations.SerializedName;
import se.lth.cs.tycho.settings.*;

import java.nio.file.Path;
import java.util.Optional;

public class PartitionSettings {



    public static PathSetting jsonConfig =  new PathSetting() {
        @Override
        public String getKey() {
            return "config";
        }

        @Override
        public String getDescription() {
            return "path to json configuration";
        }

        @Override
        public Path defaultValue(Configuration configuration) {
            return null;
        }
    };
    public enum Mode {
        @SerializedName("homogeneous")
        HOMOGENEOUS,
        @SerializedName("heterogeneous")
        HETEROGENEOUS,
        @SerializedName("pinned_heterogeneous")
        PINNED_HETEROGENEOUS

    }


}