#include "actors-rts.h"
#include <stdio.h>

typedef struct {

  AbstractActorInstance base;
  uint32_t tx_size;
  int32_t *buffer;
} ActortInstance_PassThrough;


ART_ACTION_CONTEXT(1, 1);

ART_ACTION_SCHEDULER(PassThrough_action_scheduler) {

  ActortInstance_PassThrough *thisActor = (ActortInstance_PassThrough *)pBase;

  const int *result = EXIT_CODE_YIELD;

  ART_ACTION_SCHEDULER_ENTER(1, 1);

  uint32_t availIn = pinAvailIn_int32_t(ART_INPUT(0));
  uint32_t availOut = pinAvailOut_int32_t(ART_OUTPUT(0));

  if (availIn >= thisActor->tx_size && availOut >= thisActor->tx_size) {

    ART_ACTION_ENTER(pass, 0);

    pinReadRepeat_int32_t(ART_INPUT(0), thisActor->buffer, thisActor->tx_size);
    thisActor->buffer[2] = 2;
    pinWriteRepeat_int32_t(ART_OUTPUT(0), thisActor->buffer, thisActor->tx_size);

    ART_ACTION_EXIT(pass, 0);
  }

  ART_ACTION_SCHEDULER_EXIT(1, 1);
  return result;

}


static void constructor(AbstractActorInstance *pBase) {

  ActortInstance_PassThrough *thisActor = (ActortInstance_PassThrough *)pBase;
  thisActor->tx_size = BUFFER_SIZE / 4;
  thisActor->buffer  = (int32_t *) calloc(thisActor->tx_size, sizeof(int32_t));


}

static void destructor(AbstractActorInstance *pBase) {

  ActortInstance_PassThrough *thisActor = (ActortInstance_PassThrough *)pBase;
  free(thisActor->buffer);
}

static const PortDescription inputPortDescription[] = {
  {0, "In", sizeof(int32_t)}
};

static const PortDescription outputPortDescription[] = {
  {0, "Out", sizeof(int32_t)}
};

static const int inputPortRate[] = {BUFFER_SIZE / 4};
static const int outputPortRate[] = {BUFFER_SIZE / 4};

static const ActionDescription actionDescription[] = {
  {"pass", "pass", inputPortRate, outputPortRate,0, 0}
};

ActorClass ActorClass_PassThrough = INIT_ActorClass(
  "PassThrough", 
  ActortInstance_PassThrough, 
  constructor,
  0,
  PassThrough_action_scheduler,
  destructor,
  1, inputPortDescription,
  1, outputPortDescription,
  1, actionDescription,
  0, 0, 0, 0
);