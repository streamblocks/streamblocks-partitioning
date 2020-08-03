`default_nettype none
`timescale 1 ns / 1 ps

module BandwidthTester_control_s_axi #(
	parameter integer C_S_AXI_ADDR_WIDTH = 7,
	parameter integer C_S_AXI_DATA_WIDTH = 32
)
(
	// -- axi4 lite slave signals
	input  wire                          ACLK,
	input  wire                          ARESET,
	input  wire                          ACLK_EN,
	input  wire [C_S_AXI_ADDR_WIDTH-1:0] AWADDR,
	input  wire                          AWVALID,
	output wire                          AWREADY,
	input  wire [C_S_AXI_DATA_WIDTH-1:0] WDATA,
	input  wire [C_S_AXI_DATA_WIDTH/8-1:0] WSTRB,
	input  wire                          WVALID,
	output wire                          WREADY,
	output wire [1:0]                    BRESP,
	output wire                          BVALID,
	input  wire                          BREADY,
	input  wire [C_S_AXI_ADDR_WIDTH-1:0] ARADDR,
	input  wire                          ARVALID,
	output wire                          ARREADY,
	output wire [C_S_AXI_DATA_WIDTH-1:0] RDATA,
	output wire [1:0]                    RRESP,
	output wire                          RVALID,
	input  wire                          RREADY,
	output  wire    [31 : 0]    source_Out_pass_In_requested_size,
	output  wire    [63 : 0]    source_Out_pass_In_size,
	output  wire    [63 : 0]    source_Out_pass_In_buffer,
	output  wire    [32 - 1 : 0]    pass_Out_sink_In_available_size,
	output  wire    [64 - 1 : 0]    pass_Out_sink_In_size,
	output  wire    [64 - 1 : 0]    pass_Out_sink_In_buffer,
	output  wire    [64 - 1 : 0]    kernel_command,
	output  wire    ap_start,
	input   wire    ap_done,
	input   wire    ap_ready,
	input   wire    ap_idle,
	output  wire    event_start,
	output  wire    interrupt
);

// -- --------------------------------------------------------------------------
// -- Local Parameters
// -- --------------------------------------------------------------------------

localparam
	ADDR_AP_CTRL = 7'h0,
	ADDR_GIE = 7'h4,
	ADDR_IER = 7'h8,
	ADDR_ISR = 7'hc,
	ADDR_SOURCE_OUT_PASS_IN_REQUESTED_SIZE_DATA_0 = 7'h10,
	ADDR_SOURCE_OUT_PASS_IN_REQUESTED_SIZE_CTRL = 7'h14,
	ADDR_PASS_OUT_SINK_IN_AVAILABLE_SIZE_DATA_0 = 7'h18,
	ADDR_PASS_OUT_SINK_IN_AVAILABLE_SIZE_CTRL = 7'h1c,
	ADDR_SOURCE_OUT_PASS_IN_SIZE_DATA_0 = 7'h20,
	ADDR_SOURCE_OUT_PASS_IN_SIZE_DATA_1 = 7'h24,
	ADDR_SOURCE_OUT_PASS_IN_SIZE_CTRL = 7'h28,
	ADDR_SOURCE_OUT_PASS_IN_BUFFER_DATA_0 = 7'h2c,
	ADDR_SOURCE_OUT_PASS_IN_BUFFER_DATA_1 = 7'h30,
	ADDR_SOURCE_OUT_PASS_IN_BUFFER_CTRL = 7'h34,
	ADDR_PASS_OUT_SINK_IN_SIZE_DATA_0 = 7'h38,
	ADDR_PASS_OUT_SINK_IN_SIZE_DATA_1 = 7'h3c,
	ADDR_PASS_OUT_SINK_IN_SIZE_CTRL = 7'h40,
	ADDR_PASS_OUT_SINK_IN_BUFFER_DATA_0 = 7'h44,
	ADDR_PASS_OUT_SINK_IN_BUFFER_DATA_1 = 7'h48,
	ADDR_PASS_OUT_SINK_IN_BUFFER_CTRL = 7'h4c,
	ADDR_KERNEL_COMMAND_DATA_0 = 7'h50,
	ADDR_KERNEL_COMMAND_DATA_1 = 7'h54,
	ADDR_KERNEL_COMMAND_CTRL = 7'h58,
	WRIDLE = 2'd0,
	WRDATA = 2'd1,
	WRRESP = 2'd2,
	WRRESET = 2'd3,
	RDIDLE = 2'd0,
	RDDATA = 2'd1,
	RDRESET = 2'd2,
	ADDR_BITS = 7;

// -- --------------------------------------------------------------------------
// -- Wires and Variables
// -- --------------------------------------------------------------------------

reg  [1:0] wstate = WRRESET;
reg  [1:0] wnext;
reg  [ADDR_BITS-1:0] waddr;
wire [31:0] wmask;
wire aw_hs;
wire w_hs;
reg  [1:0] rstate = RDRESET;
reg  [1:0] rnext;
reg  [31:0] rdata;
wire ar_hs;
wire [ADDR_BITS-1:0] raddr;
// -- internal registers
reg int_event_start = 1'b0;
reg int_ap_idle;
reg int_ap_ready;
reg int_ap_done = 1'b0;
reg int_ap_start = 1'b0;
reg int_auto_restart = 1'b0;
reg int_gie = 1'b0;
reg [1:0] int_ier = 2'b0;
reg [1:0] int_isr = 2'b0;

// -- Internal Registers for addresses for I/O
reg [31 : 0]    int_source_Out_pass_In_requested_size = 32'd0;
reg [63 : 0]    int_source_Out_pass_In_size = 64'd0;
reg [63 : 0]    int_source_Out_pass_In_buffer = 64'd0;
reg [31 : 0]    int_pass_Out_sink_In_available_size = 32'd0;
reg [63 : 0]    int_pass_Out_sink_In_size = 64'd0;
reg [63 : 0]    int_pass_Out_sink_In_buffer = 64'd0;
// -- kernel command
reg [63:0]    int_kernel_command = 64'd0;
 // -- external memories

// -- --------------------------------------------------------------------------
// -- Begin RTL Body
// -- --------------------------------------------------------------------------

// ------------------------------------------------------------------------
// -- AXI Write FSM
assign AWREADY = (wstate == WRIDLE);
assign WREADY  = (wstate == WRDATA);
assign BRESP   = 2'b00;  // -- OKAY
assign BVALID  = (wstate == WRRESP);
assign wmask   = { {8{WSTRB[3]}}, {8{WSTRB[2]}}, {8{WSTRB[1]}}, {8{WSTRB[0]}} };
assign aw_hs   = AWVALID & AWREADY;
assign w_hs    = WVALID & WREADY;

// -- Write state
always @(posedge ACLK) begin
	if (ARESET)
		wstate <= WRRESET;
	 else if (ACLK_EN)
		wstate <= wnext;
end

// -- Write next
always @(*) begin
	case (wstate)
		WRIDLE:
			if (AWVALID)
				wnext = WRDATA;
			else
				wnext = WRIDLE;
		WRDATA:
			if (WVALID)
				wnext = WRRESP;
			else
				wnext = WRDATA;
		WRRESP:
			if (BREADY)
				wnext = WRIDLE;
			else
				wnext = WRRESP;
		default:
			wnext = WRIDLE;
	endcase
end

// -- Write address
always @(posedge ACLK) begin
	if (ACLK_EN) begin
		if (aw_hs)
			waddr <= AWADDR[ADDR_BITS-1:0];
	end
end

// ------------------------------------------------------------------------
// -- AXI Read FSM
assign  ARREADY = (rstate == RDIDLE);
assign  RDATA   = rdata;
assign  RRESP   = 2'b00;  // OKAY
assign  RVALID  = (rstate == RDDATA);
assign  ar_hs   = ARVALID & ARREADY;
assign  raddr   = ARADDR[ADDR_BITS-1:0];

// -- Read state
always @(posedge ACLK) begin
	if (ARESET)
		rstate <= RDRESET;
	else if (ACLK_EN)
		rstate <= rnext;
end

// -- Read next
always @(*) begin
	case (rstate)
		RDIDLE:
			if (ARVALID)
				rnext = RDDATA;
			else
				rnext = RDIDLE;
		RDDATA:
			if (RREADY & RVALID)
				rnext = RDIDLE;
			else
				rnext = RDDATA;
		default:
			 rnext = RDIDLE;
	endcase
end

// -- Read data
always @(posedge ACLK) begin
	if (ACLK_EN) begin
		if (ar_hs) begin
			rdata <= 1'b0;
			case (raddr)
				ADDR_AP_CTRL: begin
					rdata[0] <= int_ap_start;
					rdata[1] <= int_ap_done;
					rdata[2] <= int_ap_idle;
					rdata[3] <= int_ap_ready;
					rdata[7] <= int_auto_restart;
				end
				ADDR_GIE: begin
					rdata <= int_gie;
				end
				ADDR_IER: begin
					rdata <= int_ier;
				end
				ADDR_ISR: begin
					rdata <= int_isr;
				end
				ADDR_SOURCE_OUT_PASS_IN_REQUESTED_SIZE_DATA_0: begin
					rdata <= int_source_Out_pass_In_requested_size[31:0];
				end
				ADDR_SOURCE_OUT_PASS_IN_SIZE_DATA_0: begin
					rdata<= int_source_Out_pass_In_size[31:0];
				end
				ADDR_SOURCE_OUT_PASS_IN_SIZE_DATA_1: begin
					rdata<= int_source_Out_pass_In_size[63:32];
				end
				ADDR_SOURCE_OUT_PASS_IN_BUFFER_DATA_0: begin
					rdata <= int_source_Out_pass_In_buffer[31:0];
				end
				ADDR_SOURCE_OUT_PASS_IN_BUFFER_DATA_1: begin
					rdata <= int_source_Out_pass_In_buffer[63:32];
				end
				ADDR_PASS_OUT_SINK_IN_AVAILABLE_SIZE_DATA_0: begin
					rdata <= int_pass_Out_sink_In_available_size[31:0];
				end
				ADDR_PASS_OUT_SINK_IN_SIZE_DATA_0: begin
					rdata <= int_pass_Out_sink_In_size[31:0];
				end
				ADDR_PASS_OUT_SINK_IN_SIZE_DATA_1: begin
					rdata <= int_pass_Out_sink_In_size[63:32];
				end
				ADDR_PASS_OUT_SINK_IN_BUFFER_DATA_0: begin
					rdata <= int_pass_Out_sink_In_buffer[31:0];
				end
				ADDR_PASS_OUT_SINK_IN_BUFFER_DATA_1: begin
					rdata <= int_pass_Out_sink_In_buffer[63:32];
				end
				ADDR_KERNEL_COMMAND_DATA_0: begin
					rdata <= int_kernel_command[31:0];
				end
				ADDR_KERNEL_COMMAND_DATA_1: begin
					rdata <= int_kernel_command[63:32];
				end
			endcase
		end
	end
end

// -- --------------------------------------------------------------------------
// -- Register Logic
// -- --------------------------------------------------------------------------
assign interrupt    = int_gie & (|int_isr);
assign event_start  = int_event_start;
assign ap_start     = int_ap_start;
assign source_Out_pass_In_requested_size = int_source_Out_pass_In_requested_size;
assign source_Out_pass_In_size = int_source_Out_pass_In_size;
assign source_Out_pass_In_buffer = int_source_Out_pass_In_buffer;
assign pass_Out_sink_In_available_size = int_pass_Out_sink_In_available_size;
assign pass_Out_sink_In_size = int_pass_Out_sink_In_size;
assign pass_Out_sink_In_buffer = int_pass_Out_sink_In_buffer;

assign kernel_command = int_kernel_command;
// -- int_event_start
always @(posedge ACLK) begin
	if (ARESET)
		int_event_start <= 1'b0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0] && WDATA[0])
			int_event_start <= 1'b1;
		else
			int_event_start <= 1'b0; // -- self clear
	end
end

// -- int_ap_start
always @(posedge ACLK) begin
	if (ARESET)
		int_ap_start <= 1'b0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0] && WDATA[0])
			int_ap_start <= 1'b1;
		else if (ap_ready)
			int_ap_start <= int_auto_restart; // clear on handshake/auto restart
	end
end

// -- int_ap_done
always @(posedge ACLK) begin
	if (ARESET)
		int_ap_done <= 1'b0;
	else if (ACLK_EN) begin
		if (ap_done)
			int_ap_done <= 1'b1;
		else if (ar_hs && raddr == ADDR_AP_CTRL)
			int_ap_done <= 1'b0; // -- clear on read
	end
end

// -- int_ap_idle
always @(posedge ACLK) begin
	if (ARESET)
		int_ap_idle <= 1'b0;
	else if (ACLK_EN) begin
		int_ap_idle <= ap_idle;
	end
end
// -- int_ap_ready
always @(posedge ACLK) begin
	if (ARESET)
		int_ap_ready <= 1'b0;
	else if (ACLK_EN) begin
		int_ap_ready <= ap_ready;
	end
end
// -- int_auto_restart
always @(posedge ACLK) begin
	if (ARESET)
		int_auto_restart <= 1'b0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0])
			int_auto_restart <=  WDATA[7];
	end
end
// -- int_gie
always @(posedge ACLK) begin
	if (ARESET)
		int_gie <= 1'b0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_GIE && WSTRB[0])
			int_gie <= WDATA[0];
	end
end

// -- int_ier
always @(posedge ACLK) begin
	if (ARESET)
		int_ier <= 1'b0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_IER && WSTRB[0])
			 int_ier <= WDATA[1:0];
	end
end

// -- int_isr[0]
always @(posedge ACLK) begin
	if (ARESET)
		int_isr[0] <= 1'b0;
	else if (ACLK_EN) begin
		if (int_ier[0] & ap_done)
			int_isr[0] <= 1'b1;
		else if (w_hs && waddr == ADDR_ISR && WSTRB[0])
			int_isr[0] <= int_isr[0] ^ WDATA[0];
	end
end

// -- int_isr
always @(posedge ACLK) begin
	if (ARESET)
		int_isr[1] <= 1'b0;
	else if (ACLK_EN) begin
		if (int_ier[1] & ap_ready)
			int_isr[1] <= 1'b1;
		else if (w_hs && waddr == ADDR_ISR && WSTRB[0])
			int_isr[1] <= int_isr[1] ^ WDATA[1];
	end
end

// -- int_source_Out_pass_In_requested_size[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_source_Out_pass_In_requested_size[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_SOURCE_OUT_PASS_IN_REQUESTED_SIZE_DATA_0)
			int_source_Out_pass_In_requested_size[31:0] <= (WDATA[31:0] & wmask) | (int_source_Out_pass_In_requested_size[31:0] & ~wmask);
	end
end

// -- int_pass_Out_sink_In_available_size[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_pass_Out_sink_In_available_size[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_PASS_OUT_SINK_IN_AVAILABLE_SIZE_DATA_0)
			int_pass_Out_sink_In_available_size[31:0] <= (WDATA[31:0] & wmask) | (int_pass_Out_sink_In_available_size[31:0] & ~wmask);
	end
end

// -- int_source_Out_pass_In_size[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_source_Out_pass_In_size[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_SOURCE_OUT_PASS_IN_SIZE_DATA_0)
			int_source_Out_pass_In_size[31:0] <= (WDATA[31:0] & wmask) | (int_source_Out_pass_In_size[31:0] & ~wmask);
	end
end

// -- int_source_Out_pass_In_size[63:32]
always @(posedge ACLK) begin
	if (ARESET)
		int_source_Out_pass_In_size[63:32] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_SOURCE_OUT_PASS_IN_SIZE_DATA_1)
			int_source_Out_pass_In_size[63:32] <= (WDATA[31:0] & wmask) | (int_source_Out_pass_In_size[63:32] & ~wmask);
	end
end

// -- int_source_Out_pass_In_buffer[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_source_Out_pass_In_buffer[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_SOURCE_OUT_PASS_IN_BUFFER_DATA_0)
			int_source_Out_pass_In_buffer[31:0] <= (WDATA[31:0] & wmask) | (int_source_Out_pass_In_buffer[31:0] & ~wmask);
	end
end

// -- int_source_Out_pass_In_buffer[63:32]
always @(posedge ACLK) begin
	if (ARESET)
		int_source_Out_pass_In_buffer[63:32] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_SOURCE_OUT_PASS_IN_BUFFER_DATA_1)
			int_source_Out_pass_In_buffer[63:32] <= (WDATA[31:0] & wmask) | (int_source_Out_pass_In_buffer[63:32] & ~wmask);
	end
end

// -- int_pass_Out_sink_In_size[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_pass_Out_sink_In_size[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_PASS_OUT_SINK_IN_SIZE_DATA_0)
			int_pass_Out_sink_In_size[31:0] <= (WDATA[31:0] & wmask) | (int_pass_Out_sink_In_size[31:0] & ~wmask);
	end
end

// -- int_pass_Out_sink_In_size[63:32]
always @(posedge ACLK) begin
	if (ARESET)
		int_pass_Out_sink_In_size[63:32] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_PASS_OUT_SINK_IN_SIZE_DATA_1)
			int_pass_Out_sink_In_size[63:32] <= (WDATA[31:0] & wmask) | (int_pass_Out_sink_In_size[63:32] & ~wmask);
	end
end

// -- int_pass_Out_sink_In_buffer[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_pass_Out_sink_In_buffer[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_PASS_OUT_SINK_IN_BUFFER_DATA_0)
			int_pass_Out_sink_In_buffer[31:0] <= (WDATA[31:0] & wmask) | (int_pass_Out_sink_In_buffer[31:0] & ~wmask);
	end
end

// -- int_pass_Out_sink_In_buffer[63:32]
always @(posedge ACLK) begin
	if (ARESET)
		int_pass_Out_sink_In_buffer[63:32] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_PASS_OUT_SINK_IN_BUFFER_DATA_1)
			int_pass_Out_sink_In_buffer[63:32] <= (WDATA[31:0] & wmask) | (int_pass_Out_sink_In_buffer[63:32] & ~wmask);
	end
end

// -- int_kernel_command[31:0]
always @(posedge ACLK) begin
	if (ARESET)
		int_kernel_command[31:0] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_KERNEL_COMMAND_DATA_0)
			int_kernel_command[31:0] <= (WDATA[31:0] & wmask) | (int_kernel_command[31:0] & ~wmask);
	end
end

// -- int_kernel_command[63:32]
always @(posedge ACLK) begin
	if (ARESET)
		int_kernel_command[63:32] <= 0;
	else if (ACLK_EN) begin
		if (w_hs && waddr == ADDR_KERNEL_COMMAND_DATA_1)
			int_kernel_command[63:32] <= (WDATA[31:0] & wmask) | (int_kernel_command[63:32] & ~wmask);
	end
end


endmodule
`default_nettype wire
