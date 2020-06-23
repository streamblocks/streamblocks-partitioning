

#include "actors-rts.h"
#include "cycle.h"
#include <errno.h>
#include <stdio.h>

typedef struct {
  AbstractActorInstance base;
  uint32_t tx_size;
  uint32_t *buffer;
  ticks total_ticks;
  uint32_t repeats;
} ActorInstance_TimedSink;

static const int exitcode_block_In_1[] = {EXITCODE_BLOCK(1), 0, 1};

ART_ACTION_CONTEXT(1, 0);

ART_ACTION_SCHEDULER(TimedSink_action_scheduler) {
  ActorInstance_TimedSink *thisActor = (ActorInstance_TimedSink *)pBase;
  const int *result = EXIT_CODE_YIELD;
  int numTokens;

  ART_ACTION_SCHEDULER_ENTER(1, 0);
  numTokens = pinAvailIn_int32_t(ART_INPUT(0));

  ART_ACTION_SCHEDULER_LOOP {
    ART_ACTION_SCHEDULER_LOOP_TOP;

    if (numTokens >= thisActor->tx_size) {
      numTokens -= thisActor->tx_size;
      ART_ACTION_ENTER(action1, 0);
      pinReadRepeat_uint32_t(ART_INPUT(0), thisActor->buffer,
                             thisActor->tx_size);
      ticks start_time =
          thisActor->buffer[0] | (ticks(thisActor->buffer[1]) << 32);
      ticks end_time = getticks();
      ticks duration = elapsed(end_time, start_time);
      thisActor->total_ticks += duration;
      thisActor->repeats++;
      // printf("transfer took %lu ticks\n", duration);
      ART_ACTION_EXIT(action1, 0);
    } else {
      result = exitcode_block_In_1;
      goto out;
    }
    ART_ACTION_SCHEDULER_LOOP_BOTTOM;
  }
out:
  ART_ACTION_SCHEDULER_EXIT(1, 0)
  return result;
}

static void constructor(AbstractActorInstance *pBase) {
  ActorInstance_TimedSink *thisActor = (ActorInstance_TimedSink *)pBase;

  thisActor->tx_size = BUFFER_SIZE / 4;
  thisActor->buffer = (uint32_t *)calloc(thisActor->tx_size, sizeof(uint32_t));
  thisActor->total_ticks = 0;
  thisActor->repeats = 0;
}

static void destructor(AbstractActorInstance *pBase) {
  ActorInstance_TimedSink *thisActor = (ActorInstance_TimedSink *)pBase;
  ticks ticks_per_transfer = thisActor->total_ticks / thisActor->repeats;
  printf("transfer ticks: %lu (repeated %lu times)\n", ticks_per_transfer,
         thisActor->repeats);
  char filename[1024];
  sprintf(filename, "dump_%d_%d.xml", BUFFER_SIZE, NUM_LOOPS);
  FILE *dump = fopen(filename, "w");
  if (dump == 0) {
    printf("Could not save results to %s\n", filename);
  } else {
    printf("Dumping result to %s\n", filename);
    fprintf(dump,
            "<Connection ticks=\"%llu\" repeats=\"%d\" buffer-size=\"%d\" />",
            ticks_per_transfer, NUM_LOOPS, BUFFER_SIZE);
    fclose(dump);
  }
  free(thisActor->buffer);
}

static void setParam(AbstractActorInstance *pBase, const char *paramName,
                     const char *value) {
  ActorInstance_TimedSink *thisActor = (ActorInstance_TimedSink *)pBase;
}

static const PortDescription inputPortDescriptions[] = {
    {0, "In", sizeof(int32_t)}};

static const int portRate_1[] = {1};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_action1[] = {};

// -- Defines by Transition
static const int defines_in_action1[] = {};

static const ActionDescription actionDescriptions[] = {
    {"action1", "action1", portRate_1, 0, uses_in_action1, defines_in_action1}};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_TimedSink = INIT_ActorClass(
    "TimedSink", ActorInstance_TimedSink, constructor, setParam,
    TimedSink_action_scheduler, destructor, 1, inputPortDescriptions, 0, 0, 1,
    actionDescriptions, 0, conditionDescription, 0, stateVariableDescription);
