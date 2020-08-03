#include "actors-rts.h"
#include "natives.h"
#include "globals.h"
#include <stdbool.h>

#define IN0_In ART_INPUT(0)

// -- Action Context structure
ART_ACTION_CONTEXT(1,0)

// -- Instance state
typedef struct{
	AbstractActorInstance base;
	int32_t program_counter;
	// -- Scope 0
	// -- Scope 1
	uint32_t a_t__8;
} ActorInstance_sink;

// -- Callables Prototypes
// -- Transition prototypes
ART_ACTION(sink_transition_0, ActorInstance_sink);
ART_ACTION_SCHEDULER(sink_scheduler);
static void ActorInstance_sink_constructor(AbstractActorInstance *);
static void ActorInstance_sink_destructor(AbstractActorInstance *);

// -- Callables
// -- Input & Output Port Description
static const PortDescription inputPortDescriptions[]={
	{0, "In", (sizeof(uint32_t))
	#ifdef CAL_RT_CALVIN
	, NULL
	#endif
	},
};

// -- State Variable Description
static const StateVariableDescription stateVariableDescription[] = {
};

// -- Input / Output Port Rate by Transition
static const int portRate_in_sink_transition_0[] = {1};

static const int portRate_out_sink_transition_0[] = {};

// -- Uses & defines in Transition
static const int uses_in_sink_transition_0[] = {};

static const int defines_in_sink_transition_0[] = {};

// -- Transitions Description
static const ActionDescription actionDescriptions[] = {
	{"sink_transition_0", "$untagged12", portRate_in_sink_transition_0, portRate_out_sink_transition_0, uses_in_sink_transition_0, defines_in_sink_transition_0},
};

// -- State variables in Condition
// -- Condition description
static const ConditionDescription conditionDescription[] = {
	{"sink_condition_0", INPUT_KIND, 0, 1, NULL},
};

// -- Scopes
#ifndef TRACE_TURNUS
ART_SCOPE(sink_init_scope_1, ActorInstance_sink){
	{
		thisActor->a_t__8 = pinPeekFront_uint32_t(IN0_In);
	}
}
#else
ART_SCOPE(sink_init_scope_1, ActorInstance_sink){
	{
		OpCounters *__opCounters = new OpCounters();
		thisActor->a_t__8 = pinPeekFront_uint32_t(IN0_In);
		delete __opCounters;
	}
}
#endif

// -- Conditions
#ifndef TRACE_TURNUS
ART_CONDITION(sink_condition_0, ActorInstance_sink){
	ART_CONDITION_ENTER(sink_condition_0, 0)
	bool cond = pinAvailIn_uint32_t(IN0_In) >=  1;
	ART_CONDITION_EXIT(sink_condition_0, 0)
	return cond;
}
#else
ART_CONDITION(sink_condition_0, ActorInstance_sink){
	ART_CONDITION_ENTER(sink_condition_0, 0)
	bool cond = pinAvailIn_uint32_t(IN0_In) >=  1;
	ART_CONDITION_EXIT(sink_condition_0, 0)
	return cond;
}
#endif

// -- Actor Class
#ifdef CAL_RT_CALVIN
ActorClass klass
#else
ActorClass ActorClass_sink
#endif
	= INIT_ActorClass(
		(char*) "profiling.hardware.loopback.Sink",
		ActorInstance_sink,
		ActorInstance_sink_constructor,
		0, // -- setParam not needed anymore (we instantiate with params)
		sink_scheduler,
		ActorInstance_sink_destructor,
		1, inputPortDescriptions,
		0, 0,
		1, actionDescriptions,
		1, conditionDescription,
		0, stateVariableDescription
	);

// -- Transitions Definitions
#ifndef TRACE_TURNUS
ART_ACTION(sink_transition_0, ActorInstance_sink){
	ART_ACTION_ENTER(sink_transition_0, 0);
	pinConsume_uint32_t(IN0_In);
	ART_ACTION_EXIT(sink_transition_0, 0);
}
#else
ART_ACTION(sink_transition_0, ActorInstance_sink){
	ART_ACTION_ENTER(sink_transition_0, 0);
	pinConsume_uint32_t(IN0_In);
	ART_ACTION_EXIT(sink_transition_0, 0);
}
#endif

// -- Constructor Definitions
static void ActorInstance_sink_constructor(AbstractActorInstance *pBase){
	ActorInstance_sink *thisActor = (ActorInstance_sink*) pBase;
	// -- Actor Machine Initial Program Counter
	thisActor->program_counter = 0;

	#ifdef CAL_RT_CALVIN
	init_global_variables();
	#endif

	// -- Initialize persistent scopes
}

// -- Constructor Definitions
static void ActorInstance_sink_destructor(AbstractActorInstance *pBase){
	ActorInstance_sink *thisActor = (ActorInstance_sink*) pBase;
}

// -- Scheduler Definitions
static const int exitcode_block_Any[3] = {1,0,1};

ART_ACTION_SCHEDULER(sink_scheduler){
	const int *result = EXIT_CODE_YIELD;

	ActorInstance_sink *thisActor = (ActorInstance_sink*) pBase;
	ART_ACTION_SCHEDULER_ENTER(1, 0)
	switch (thisActor->program_counter) {
	case 0: goto S0;
	}

	S0:
	if (ART_TEST_CONDITION(sink_condition_0)) {
		goto S1;
	} else {
		static const int exitCode[] = {EXITCODE_BLOCK(1), 0, 1};
		result = exitCode;
		goto S2;
	}

	S1:
	ART_EXEC_TRANSITION(sink_transition_0);
	goto S0;

	S2:
	thisActor->program_counter = 0;
	goto out;

	out:
	ART_ACTION_SCHEDULER_EXIT(1, 0)
	return result;
}
