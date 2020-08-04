#include "actors-rts.h"
#include "globals.h"
#include "natives.h"
#include <stdbool.h>

#include "device-handle.h"
#define MIN(a, b) (a < b ? a : b)
#define IN0_source_Out_pass_In ART_INPUT(0)
#define OUT0_pass_Out_sink_In ART_OUTPUT(0)

cl_ulong getEventElapsedTime(cl_event event) {

  cl_ulong enqueue_time = 0;
  cl_ulong complete_time = 0;
  OCL_CHECK(clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_QUEUED,
                                    sizeof(cl_ulong), &enqueue_time, NULL));
  OCL_CHECK(clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_END,
                                    sizeof(cl_ulong), &complete_time, NULL));

  return complete_time - enqueue_time;
}
double computeBandwidth(cl_ulong transfer_bytes, cl_ulong time_ns) {

  double MiBytes = (double(transfer_bytes)) / 1024. / 1024.;
  double seconds = double(time_ns) * 1e-9;
  return MiBytes / seconds;
}
// -- Action Context structure
ART_ACTION_CONTEXT(1, 1)

// -- Instance state structure
typedef struct {
  AbstractActorInstance base;
  int32_t program_counter;
  // -- device handle object
  DeviceHandle_t dev;
  size_t cl_write_buffer_size[1];
  size_t cl_read_buffer_size[1];
  uint32_t source_Out_pass_In_request_size;

  // -- buffer and transaction size for port source_Out_pass_In
  uint32_t *source_Out_pass_In_buffer;
  uint32_t *source_Out_pass_In_size;
  // -- buffer and transaction size for port pass_Out_sink_In
  uint32_t *pass_Out_sink_In_buffer;
  uint32_t *pass_Out_sink_In_size;
  size_t pass_Out_sink_In_offset;

  uint64_t total_consumed;
  uint64_t total_produced;
  uint64_t total_request;
  bool should_retry;

  uint64_t loop_counter;
  cl_ulong kernel_time;
  cl_ulong write_time;
  cl_ulong read_time;
  cl_ulong size_read_time;
} ActorInstance_system_plink_0;

// -- scheduler prototype
ART_ACTION_SCHEDULER(ActorInstance_system_plink_0_scheduler);

// -- constructor and destructor prototype
static void ActorInstance_system_plink_0_constructor(AbstractActorInstance *);
static void ActorInstance_system_plink_0_destructor(AbstractActorInstance *);

// -- Input & Output Port Description
static const PortDescription inputPortDescriptions[] = {
    {0, "source_Out_pass_In", (sizeof(uint32_t))
#ifdef CAL_RT_CALVIN
                                  ,
     NULL
#endif
    },
};

static const PortDescription outputPortDescriptions[] = {
    {0, "pass_Out_sink_In", (sizeof(uint32_t))
#ifdef CAL_RT_CALVIN
                                ,
     NULL
#endif
    },
};

// -- state variable description
static const StateVariableDescription stateVariableDescriptions[] = {};
// -- Transitions Description
static const ActionDescription actionDescriptions[] = {};
// -- Condition description
static const ConditionDescription conditionDescriptions[] = {};
// -- Actor Class
#ifdef CAL_RT_CALVIN
ActorClass klass
#else
ActorClass ActorClass_system_plink_0
#endif
    = INIT_ActorClass(
        "system_plink_0", ActorInstance_system_plink_0,
        ActorInstance_system_plink_0_constructor,
        0, // -- setParam not needed anymore (we instantiate with params)
        ActorInstance_system_plink_0_scheduler,
        ActorInstance_system_plink_0_destructor, 1, inputPortDescriptions, 1,
        outputPortDescriptions, 0, actionDescriptions, 0, conditionDescriptions,
        0, stateVariableDescriptions);

// -- Constructor definition
static void
ActorInstance_system_plink_0_constructor(AbstractActorInstance *pBase) {
  ActorInstance_system_plink_0 *thisActor =
      (ActorInstance_system_plink_0 *)pBase;
  thisActor->program_counter = 0;
  thisActor->cl_write_buffer_size[0] =
      BUFFER_SIZE_WORDS; // -- cl buffer size for port source_Out_pass_In
  thisActor->cl_read_buffer_size[0] =
      BUFFER_SIZE_WORDS; // -- cl buffer size for port pass_Out_sink_In

  // -- Construct the FPGA device handle
  DeviceHandleConstructor(&thisActor->dev, 1, 1, "BandwidthTester_kernel",
                          "xilinx_kcu1500_dynamic_5_0", "xclbin", false);

  // -- allocate CL buffers
  DeviceHandleCreateCLBuffers(&thisActor->dev, thisActor->cl_write_buffer_size,
                              thisActor->cl_read_buffer_size);

  // -- allocate Host buffers
  thisActor->source_Out_pass_In_buffer = (uint32_t *)aligned_alloc(
      MEM_ALIGNMENT, thisActor->cl_write_buffer_size[0] * sizeof(uint32_t));
  DeviceHandleSet_source_Out_pass_In_buffer_ptr(
      &thisActor->dev, thisActor->source_Out_pass_In_buffer);

  thisActor->pass_Out_sink_In_buffer = (uint32_t *)aligned_alloc(
      MEM_ALIGNMENT, thisActor->cl_read_buffer_size[0] * sizeof(uint32_t));
  DeviceHandleSet_pass_Out_sink_In_buffer_ptr(
      &thisActor->dev, thisActor->pass_Out_sink_In_buffer);
  // -- allocate buffers for TX/RX size buffers
  thisActor->source_Out_pass_In_size =
      (uint32_t *)aligned_alloc(MEM_ALIGNMENT, sizeof(uint32_t));
  DeviceHandleSet_source_Out_pass_In_size_ptr(
      &thisActor->dev, thisActor->source_Out_pass_In_size);
  thisActor->pass_Out_sink_In_size =
      (uint32_t *)aligned_alloc(MEM_ALIGNMENT, sizeof(uint32_t));
  DeviceHandleSet_pass_Out_sink_In_size_ptr(&thisActor->dev,
                                            thisActor->pass_Out_sink_In_size);

  thisActor->total_consumed = 0;
  thisActor->total_produced = 0;
  thisActor->total_request = 0;
  thisActor->should_retry = false;

  thisActor->loop_counter = 0;

  thisActor->kernel_time = 0;
  thisActor->write_time = 0;
  thisActor->read_time = 0;
  thisActor->size_read_time = 0;
#ifdef CAL_RT_CALVIN
  init_global_variables();
#endif
}

// -- destructor definition
static void
ActorInstance_system_plink_0_destructor(AbstractActorInstance *pBase) {
  ActorInstance_system_plink_0 *thisActor =
      (ActorInstance_system_plink_0 *)pBase;

  DeviceHandleTerminate(&thisActor->dev);

  cl_ulong num_bytes = BUFFER_SIZE * thisActor->loop_counter;
  double kernel_bw = computeBandwidth(num_bytes, thisActor->kernel_time);
  double write_bw = computeBandwidth(num_bytes, thisActor->write_time);
  double read_bw = computeBandwidth(num_bytes, thisActor->read_time);
  double size_read_bw =
      computeBandwidth(thisActor->loop_counter * 2 * sizeof(uint32_t),
                       thisActor->size_read_time);

  printf("Bandwidth report:\n");
  printf("Kernel: %6.3f MiB/s\n", kernel_bw);
  printf("Write : %6.3f MiB/s\n", write_bw);
  printf("Read  : %6.3f MiB/s\n", read_bw);
  printf("Size  : %6.3f  KiB/s\n", size_read_bw * 1024.);

  char filename[1024];
  sprintf(filename, "dump_%d_%d.xml", BUFFER_SIZE, NUM_LOOPS);
  FILE *dump = fopen(filename, "w");
  if (dump == 0) {
    printf("Could not save result to file %s\n", filename);
  } else {
    printf("Dumping result to %s\n", filename);
    fprintf(dump, "<Connection kernel-total=\"%llu\" write-total=\"%llu\" "
                  "read-total=\"%llu\" read-size-total=\"%llu\" "
                  "repeats=\"%lu\" buffer-size=\"%lu\" />",
            thisActor->kernel_time, thisActor->write_time, thisActor->read_time,
            thisActor->size_read_time, thisActor->loop_counter, BUFFER_SIZE);
    fclose(dump);
  }
  free(thisActor->source_Out_pass_In_buffer);
  free(thisActor->source_Out_pass_In_size);
  free(thisActor->pass_Out_sink_In_buffer);
  free(thisActor->pass_Out_sink_In_size);
}

// -- scheduler definitions
static const int exitcode_block_any[3] = {1, 0, 1};

ART_ACTION_SCHEDULER(ActorInstance_system_plink_0_scheduler) {
  const int *result = exitcode_block_any;
  static const int exitCode[] = {EXITCODE_BLOCK(1), 0, 1};

  ActorInstance_system_plink_0 *thisActor =
      (ActorInstance_system_plink_0 *)pBase;

  ART_ACTION_SCHEDULER_ENTER(1, 1)

  switch (thisActor->program_counter) {
  case 0:
    goto CHECK;
  case 2:
    goto RX;
  case 3:
    goto WRITE;
  }
CHECK : {
  uint32_t tokens_source_Out_pass_In =
      pinAvailIn_uint32_t(IN0_source_Out_pass_In);
  // printf("Checking %d\n", tokens_source_Out_pass_In);
  bool should_start =
      (tokens_source_Out_pass_In > 0) || thisActor->should_retry;
  if (should_start) {
    thisActor->program_counter = 1;
    goto TX;
  } else {
    thisActor->program_counter = 0;
    goto YIELD;
  }
}
TX : { // -- Transmit to FPGA memory
  ART_ACTION_ENTER(TX, 0);
  // -- set request size
  thisActor->source_Out_pass_In_request_size =
      pinAvailIn_uint32_t(IN0_source_Out_pass_In);
  DeviceHandleSet_source_Out_pass_In_request_size(
      &thisActor->dev, thisActor->source_Out_pass_In_request_size);

  pinPeekRepeat_uint32_t(IN0_source_Out_pass_In,
                         thisActor->source_Out_pass_In_buffer,
                         thisActor->source_Out_pass_In_request_size);
  // printf("Launching kernel %d\n", thisActor->source_Out_pass_In_request_size);

  // -- copy the host buffers to device
  DeviceHandleEnqueueWriteBuffers(&thisActor->dev);
  // -- set the kernel args
  DeviceHandleSetArgs(&thisActor->dev);
  // -- enqueue the execution of the kernel
  DeviceHandleEnqueueExecution(&thisActor->dev);
  // -- enqueue reading of consumed and produced sizes
  DeviceHandleEnqueueReadSize(&thisActor->dev);

  thisActor->program_counter = 2;
  ART_ACTION_ENTER(TX, 0);
  goto YIELD;
}
RX : { // -- Receive from FPGA
  // -- read the produced and consumed size
  DeviceHandleWaitForReadSize(&thisActor->dev);
  // -- read the device outputs buffers

  // printf("%d %d %d\n", thisActor->source_Out_pass_In_size[0],
  //        thisActor->pass_Out_sink_In_size[0],
  //        thisActor->source_Out_pass_In_request_size);
  DeviceHandleEnqueueReadBuffers(&thisActor->dev);

  thisActor->total_request = 0;
  thisActor->total_consumed = 0;
  thisActor->total_produced = 0;
  thisActor->should_retry = false;
  // -- Consume on behalf of device
  if (thisActor->source_Out_pass_In_size[0] > 0) {
    ART_ACTION_ENTER(CONSUME, 1);
    pinConsumeRepeat_uint32_t(IN0_source_Out_pass_In,
                              thisActor->source_Out_pass_In_size[0]);
    ART_ACTION_ENTER(CONSUME, 1);
  }
  thisActor->total_consumed += thisActor->source_Out_pass_In_size[0];
  thisActor->total_request += thisActor->source_Out_pass_In_request_size;

  thisActor->pass_Out_sink_In_offset = 0;

  thisActor->total_produced += thisActor->pass_Out_sink_In_size[0];

  if (thisActor->total_produced == 0 && thisActor->total_consumed == 0 &&
      thisActor->total_request > 0)
    runtimeError(pBase, "Potential device deadlock\n");
  if (thisActor->total_produced > 0) {
    ART_ACTION_ENTER(RX, 2);
    ART_ACTION_EXIT(RX, 2);
    thisActor->program_counter = 3;
    goto WRITE;
  } else {
    thisActor->program_counter = 0;
    goto CHECK;
  }
}
WRITE : { // -- retry reading
  
  // -- wait for read transfer to complete

  DeviceHandleWaitForReadBuffers(&thisActor->dev);
  uint32_t done_reading = 0;
  size_t pass_Out_sink_In_remain =
      thisActor->pass_Out_sink_In_size[0] - thisActor->pass_Out_sink_In_offset;
  uint32_t pass_Out_sink_In_to_write =
      MIN(pass_Out_sink_In_remain, pinAvailOut_uint32_t(OUT0_pass_Out_sink_In));
  if (pass_Out_sink_In_remain > 0) { // -- if there are tokens remaining
    if (pass_Out_sink_In_to_write > 0) {
      ART_ACTION_ENTER(WRITE, 3);
      pinWriteRepeat_uint32_t(
          OUT0_pass_Out_sink_In,
          &thisActor
               ->pass_Out_sink_In_buffer[thisActor->pass_Out_sink_In_offset],
          pass_Out_sink_In_to_write);
      ART_ACTION_EXIT(WRITE, 3);
    }

    thisActor->pass_Out_sink_In_offset += pass_Out_sink_In_to_write;
    if (thisActor->pass_Out_sink_In_offset ==
        thisActor->pass_Out_sink_In_size[0]) // this port is done
      done_reading++;
  } else {
    done_reading++;
  }

  if (done_reading == 1) {
    thisActor->program_counter = 0;

    thisActor->kernel_time += getEventElapsedTime(thisActor->dev.kernel_event);

    if (thisActor->dev.write_buffer_event_info[0].active == true) {
      thisActor->loop_counter++;
      thisActor->write_time +=
          getEventElapsedTime(thisActor->dev.write_buffer_event[0]);
    }
    if (thisActor->dev.read_buffer_event_info[0].active == true)
      thisActor->read_time +=
          getEventElapsedTime(thisActor->dev.read_buffer_event[0]);

    thisActor->size_read_time +=
        (getEventElapsedTime(thisActor->dev.read_size_event[0]) +
         getEventElapsedTime(thisActor->dev.read_size_event[1]));

    DeviceHandleReleaseKernelEvent(&thisActor->dev);
    DeviceHandleReleaseWriteEvents(&thisActor->dev);
    DeviceHandleReleaseReadSizeEvents(&thisActor->dev);
    DeviceHandleReleaseReadBufferEvents(&thisActor->dev);
    // -- retry if the output buffers were full
    thisActor->should_retry =
        (thisActor->pass_Out_sink_In_size[0] == BUFFER_SIZE_WORDS);
  } else {
    thisActor->program_counter = 3;
  }
  
  goto YIELD;
}
YIELD : {
  ART_ACTION_SCHEDULER_EXIT(1, 1)
  return result;
}
}
