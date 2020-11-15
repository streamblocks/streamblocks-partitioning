package ch.epfl.vlsc.analysis.partitioning.parser;


import gurobi.GRB;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.Collections;

public class DeviceProfileDataBase extends CommonProfileDataBase{


    private PCIeProfileDataBase pciDb;

    public DeviceProfileDataBase() {

        this.pciDb = new PCIeProfileDataBase();
        this.execDb = new ExecutionProfileDataBase();

    }
    public static class PCIeTicks {

        private final Long readTicks;
        private final Long readSizeTicks;
        private final Long writeTicks;
        private final Long kernelTicks;
        private final Long repeats;
        public PCIeTicks(Long readTicks, Long readSizeTicks, Long writeTicks, Long kernelTicks, Long repeats) {
            this.readTicks = readTicks;
            this.readSizeTicks = readSizeTicks;
            this.writeTicks = writeTicks;
            this.kernelTicks = kernelTicks;
            this.repeats = repeats;
        }

        public Long getReadTicks() {
            return readTicks;
        }

        public Long getReadSizeTicks() {
            return readSizeTicks;
        }

        public Long getWriteTicks() {
            return writeTicks;
        }

        public Long getKernelTicks() {
            return kernelTicks;
        }

        public Long getRepeats() {
            return repeats;
        }
    }

    static public class PCIeProfileDataBase extends ProfileDataBase<Long, PCIeTicks> {
        @Override
        String getObjectName(Long bufferSize) {
            return String.format("buffer size %d", bufferSize);
        }
    }

    public void setPCIeTicks(Long bufferSize, PCIeTicks ticks) {
        pciDb.set(bufferSize, ticks);
    }

    /**
     * Get the estimated time of PCIe transaction in ns
     * @param bufferSize the size of the buffer in bytes
     * @return
     */
    public Double getPCIeReadTime(Long bufferSize, Long byteExchanged) {

        PCIeTicks data = getPCIeTicks(bufferSize);
        Double outputStageTicks = data.getKernelTicks().doubleValue() / 2.0;
        Double readSizeTicks = data.getReadSizeTicks().doubleValue();
        Double readTicks = data.getReadTicks().doubleValue();
        outputStageTicks = Double.valueOf(0);
        Double averageTime = (outputStageTicks + readSizeTicks + readTicks) / data.getRepeats().doubleValue();
        Double numTransfers = byteExchanged.doubleValue() / bufferSize.doubleValue();

        return averageTime * numTransfers;
    }

    public Double getPCIeWriteTime(Long bufferSize, Long byteExchanged) {
        PCIeTicks data = getPCIeTicks(bufferSize);
        Double inputStageTicks = data.kernelTicks.doubleValue() / 2.0;
        Double writeTicks = data.getWriteTicks().doubleValue();
        inputStageTicks = Double.valueOf(0);

        Double averageTime = (inputStageTicks + writeTicks) / data.getRepeats().doubleValue();
        Double numTransfers = byteExchanged.doubleValue() / bufferSize.doubleValue();
        return averageTime * numTransfers;
    }

    private PCIeTicks getPCIeTicks(Long bufferSize) {
        Long nextPowerOfTwoSize =
                (Long.highestOneBit(bufferSize) == bufferSize) ?
                        bufferSize :
                        Long.highestOneBit(bufferSize) << 1;
        if (nextPowerOfTwoSize < getMinimumPCIeBufferSize()) {
            nextPowerOfTwoSize = getMinimumPCIeBufferSize();
        }
        return this.pciDb.get(nextPowerOfTwoSize);
    }

    public Long getMinimumPCIeBufferSize() {
        return Collections.min(this.pciDb.keySet());
    }

    @Override
    public Long getInstanceTicks(Instance instance) {
        if (this.getExecutionProfileDataBase().contains(instance)) {
            return this.getExecutionProfileDataBase().get(instance);
        } else {
            return Long.valueOf(0);
        }
    }
}
