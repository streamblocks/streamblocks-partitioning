
#include "actors-rts.h"
#include "cycle.h"
#include <errno.h>
#include <stdio.h>

enum class SourceState { Sending, Receiving };
typedef struct {
  AbstractActorInstance base;
  uint32_t tx_size;
  int32_t *buffer;
  uint32_t repeat;
  SourceState state;
  ticks total_ticks;
  uint32_t num_loops;

} ActorInstance_TimedSource;

ART_ACTION_CONTEXT(1, 1);

ART_ACTION_SCHEDULER(TimedSource_action_scheduler) {
  const int *result = EXIT_CODE_YIELD;
  ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *)pBase;

  ART_ACTION_SCHEDULER_ENTER(1, 1);

  switch (thisActor->state) {

  case SourceState::Sending: {
    uint32_t availOutput = pinAvailOut_int32_t(ART_OUTPUT(0));
    if (availOutput >= thisActor->tx_size && thisActor->repeat > 0) {
      ART_ACTION_ENTER(sendAction, 0);
      ticks start_time_stamp = getticks();
      memcpy(thisActor->buffer, &start_time_stamp, sizeof(ticks));
      pinWriteRepeat_int32_t(ART_OUTPUT(0), thisActor->buffer,
                             thisActor->tx_size);
      thisActor->repeat--;
      thisActor->state = SourceState::Receiving;
      ART_ACTION_EXIT(sendAction, 1);
    }
    break;
  }

  case SourceState::Receiving: {
    uint32_t availInput = pinAvailIn_int32_t(ART_INPUT(0));
    if (availInput >= thisActor->tx_size) {
      ART_ACTION_ENTER(recvAction, 1);

      ticks end_time_stamp = getticks();
      ticks start_time_stamp;
      pinReadRepeat_int32_t(ART_INPUT(0), (int32_t*) &start_time_stamp, sizeof(ticks) / sizeof(int32_t));
      ticks round_trip_time = elapsed(end_time_stamp, start_time_stamp);
      thisActor->total_ticks += round_trip_time;
      pinConsumeRepeat_int32_t(ART_INPUT(0), thisActor->tx_size - (sizeof(ticks) / sizeof(int32_t)));
      thisActor->state = SourceState::Sending;
      ART_ACTION_EXIT(recvAction, 1);
    }
    break;
  }
  default:
    runtimeError(pBase, "Invalid state reached in the TimedSourceActor");
    break;
  }
  ART_ACTION_SCHEDULER_EXIT(1, 1);
  return result;
}

static void constructor(AbstractActorInstance *pBase) {
  ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *)pBase;

  if (BUFFER_SIZE / 4 < sizeof(ticks)) {
    runtimeError(pBase, "Buffer size is too small, minimumm size is %llu bytes",
                 sizeof(ticks));
  }

  thisActor->repeat = NUM_LOOPS;
  thisActor->num_loops = NUM_LOOPS;

  thisActor->tx_size = BUFFER_SIZE / 4;
  thisActor->buffer = (int32_t *)calloc(thisActor->tx_size, sizeof(int32_t));

  thisActor->state = SourceState::Sending;
}

static void destructor(AbstractActorInstance *pBase) {
  ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *)pBase;

  ticks ticks_per_transfer =
      (thisActor->total_ticks / ticks(thisActor->num_loops * 2));

  printf("Transfer tick: %llu (repeated %lu times)\n", ticks_per_transfer,
         thisActor->num_loops);

  char filename[1024];
  sprintf(filename, "dump_%d_%d.xml", BUFFER_SIZE, NUM_LOOPS);
  FILE *dump = fopen(filename, "w");
  if (dump == 0) {
    printf("Could not save result to file %s\n", filename);
  } else {
    printf("Dumping result to %s\n", filename);
    fprintf(dump,
            "<Connection ticks=\"%llu\" repeats=\"%lu\" buffer-size=\"%d\" />",
            ticks_per_transfer, NUM_LOOPS, BUFFER_SIZE);
    fclose(dump);
  }
  free(thisActor->buffer);
}

static const PortDescription outputPortDescriptions[] = {
    {0, "Out", sizeof(int32_t)}};

static const PortDescription inputPortDescriptions[] = {
    {0, "In", sizeof(int32_t)}};

static const int portRate_In[] = {BUFFER_SIZE / 4};

static const int portRate_Out[] = {BUFFER_SIZE / 4};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

static const ActionDescription actionDescriptions[] = {
    {"actionProduce", "actionProduce", 0, portRate_Out, 0, 0},
    {"actionConsume", "actionConsume", portRate_In, 0, 0, 0}};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_TimedSource = INIT_ActorClass(
    "TimedSource", ActorInstance_TimedSource, constructor, 0,
    TimedSource_action_scheduler, destructor, 1, inputPortDescriptions, 1,
    outputPortDescriptions, 2, actionDescriptions, 0, conditionDescription, 0,
    stateVariableDescription);
