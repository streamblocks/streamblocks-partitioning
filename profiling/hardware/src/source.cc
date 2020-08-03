#include "actors-rts.h"
#include "natives.h"
#include "globals.h"
#include <stdbool.h>

#define OUT0_Out ART_OUTPUT(0)

// -- Action Context structure
ART_ACTION_CONTEXT(0,1)

// -- Instance state
typedef struct{
	AbstractActorInstance base;
	uint32_t loop_counter;
	uint32_t *buffer;

	// -- Scope 1
} ActorInstance_source;

// -- Callables Prototypes
// -- Transition prototypes

ART_ACTION_SCHEDULER(source_scheduler);
static void ActorInstance_source_constructor(AbstractActorInstance *);
static void ActorInstance_source_destructor(AbstractActorInstance *);

// -- Callables
// -- Input & Output Port Description
static const PortDescription outputPortDescriptions[]={
	{0, "Out", (sizeof(uint32_t))
	#ifdef CAL_RT_CALVIN
	, NULL
	#endif
	},
};

// -- State Variable Description
static const StateVariableDescription stateVariableDescription[] = {
	
};

// -- Input / Output Port Rate by Transition
static const int portRate_in_source_transition_0[] = {};

static const int portRate_out_source_transition_0[] = {BUFFER_SIZE_WORDS};


// -- Transitions Description
static const ActionDescription actionDescriptions[] = {
	{"send", "send", portRate_in_source_transition_0, portRate_out_source_transition_0, 0, 0},
};

// -- State variables in Condition
static const int state_var_in_source_condition_1[] = {1};

// -- Condition description
static const ConditionDescription conditionDescription[] = {

};

// -- Actor Class
#ifdef CAL_RT_CALVIN
ActorClass klass
#else
ActorClass ActorClass_source
#endif
	= INIT_ActorClass(
		(char*) "profiling.hardware.loopback.Source_0",
		ActorInstance_source,
		ActorInstance_source_constructor,
		0, // -- setParam not needed anymore (we instantiate with params)
		source_scheduler,
		ActorInstance_source_destructor,
		0, 0,
		1, outputPortDescriptions,
		1, actionDescriptions,
		0, conditionDescription,
		0, stateVariableDescription
	);


// -- Constructor Definitions
static void ActorInstance_source_constructor(AbstractActorInstance *pBase){
	ActorInstance_source *thisActor = (ActorInstance_source*) pBase;
	// -- Actor Machine Initial Program Counter
	

	thisActor->loop_counter = 0;
	thisActor->buffer = (uint32_t *) calloc(BUFFER_SIZE_WORDS, sizeof(uint32_t));

	#ifdef CAL_RT_CALVIN
	init_global_variables();
	#endif

}

// -- Constructor Definitions
static void ActorInstance_source_destructor(AbstractActorInstance *pBase){
	ActorInstance_source *thisActor = (ActorInstance_source*) pBase;
}

// -- Scheduler Definitions
static const int exitcode_block_Any[3] = {1,0,1};

ART_ACTION_SCHEDULER(source_scheduler){
	const int *result = EXIT_CODE_YIELD;

	ActorInstance_source *thisActor = (ActorInstance_source*) pBase;
	ART_ACTION_SCHEDULER_ENTER(0, 1)
	printf("Starting source\n");
	uint32_t availOutput = pinAvailOut_uint32_t(ART_OUTPUT(0));
	if (availOutput >= BUFFER_SIZE_WORDS && thisActor->loop_counter < NUM_LOOPS) {
		printf("Sourcing %d bytes\n", BUFFER_SIZE_WORDS * sizeof(uint32_t));
		ART_ACTION_ENTER(send, 0);
		pinWriteRepeat_uint32_t(ART_OUTPUT(0), thisActor->buffer, BUFFER_SIZE_WORDS);
		thisActor->loop_counter ++;
		ART_ACTION_EXIT(send, 0);

	}
	ART_ACTION_SCHEDULER_EXIT(0, 1)
	return result;
}
