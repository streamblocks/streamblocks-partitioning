
#include "actors-rts.h"
#include <stdio.h>
#include <errno.h>
#include "cycle.h"
typedef struct {
    AbstractActorInstance base;
    uint32_t tx_size;
    uint32_t *buffer;
    uint32_t repeat;
} ActorInstance_TimedSource;

static const int exitcode_block_Out_1[] = {
        EXITCODE_BLOCK(1), 0, 1
};

ART_ACTION_CONTEXT(0, 1);

ART_ACTION_SCHEDULER(TimedSource_action_scheduler) {
    const int *result = EXIT_CODE_YIELD;
    ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *) pBase;
    int n;
    ART_ACTION_SCHEDULER_ENTER(0, 1);
    
    n = pinAvailOut_int32_t(ART_OUTPUT(0));
    ART_ACTION_SCHEDULER_LOOP {
        ART_ACTION_SCHEDULER_LOOP_TOP;
        if (n >= thisActor->tx_size && thisActor->repeat > 0) {
            ART_ACTION_ENTER(action1, 0);
            int token;
            ticks start_time = getticks();
            thisActor->buffer[0] = start_time;
            thisActor->buffer[1] = start_time >> 32;
            pinWriteRepeat_uint32_t(ART_OUTPUT(0), thisActor->buffer, thisActor->tx_size);
            n -= thisActor->tx_size;
            thisActor->repeat -- ;
            ART_ACTION_EXIT(action1, 0);
        } else {
            result = exitcode_block_Out_1;
            goto out;
        }
        ART_ACTION_SCHEDULER_LOOP_BOTTOM;
    }
    out:
    ART_ACTION_SCHEDULER_EXIT(0, 1);
    return result;

}

static void constructor(AbstractActorInstance *pBase) {
    ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *) pBase;

    
    if (thisActor->repeat == 0)
        thisActor->repeat = 1;
    thisActor->tx_size = BUFFER_SIZE / 4;
    thisActor->buffer = (uint32_t*) calloc(thisActor->tx_size, sizeof(uint32_t));
}

static void destructor(AbstractActorInstance *pBase) {
    ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *) pBase;
    free(thisActor->buffer);
}

static void setParam(AbstractActorInstance *pBase,
                     const char *paramName,
                     const char *value) {
    ActorInstance_TimedSource *thisActor = (ActorInstance_TimedSource *) pBase;
    if (strcmp(paramName, "repeat") == 0) {
        thisActor->repeat = atoi(value);
    }else {
        runtimeError(pBase, "No such parameter: %s", paramName);
    }
}

static const PortDescription outputPortDescriptions[] = {
        {0, "Out", sizeof(int32_t)}
};

static const int portRate_0[] = {
        0
};

static const int portRate_1[] = {
        1
};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_actionAtLine_7[] = {};

// -- Defines by Transition
static const int defines_in_actionAtLine_7[] = {};

static const ActionDescription actionDescriptions[] = {
        {"actionAtLine_7", "actionAtLine_7", 0, portRate_1, uses_in_actionAtLine_7, defines_in_actionAtLine_7}
};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_TimedSource = INIT_ActorClass(
        "TimedSource",
        ActorInstance_TimedSource,
        constructor,
        setParam,
        TimedSource_action_scheduler,
        destructor,
        0, 0,
        1, outputPortDescriptions,
        1, actionDescriptions,
        0, conditionDescription,
        0, stateVariableDescription
);
