// ==============================================================
// RTL generated by Vivado(TM) HLS - High-Level Synthesis from C, C++ and SystemC
// Version: 2018.2
// Copyright (C) 1986-2018 Xilinx, Inc. All Rights Reserved.
// 
// ===========================================================

`timescale 1 ns / 1 ps 

(* CORE_GENERATION_INFO="pass,hls_ip_2018_2,{HLS_INPUT_TYPE=cxx,HLS_INPUT_FLOAT=0,HLS_INPUT_FIXED=0,HLS_INPUT_PART=xcku115-flvb2104-2-e,HLS_INPUT_CLOCK=3.330000,HLS_INPUT_ARCH=others,HLS_SYN_CLOCK=2.913750,HLS_SYN_LAT=0,HLS_SYN_TPT=none,HLS_SYN_MEM=0,HLS_SYN_DSP=0,HLS_SYN_FF=1,HLS_SYN_LUT=15,HLS_VERSION=2018_2}" *)

module pass (
        ap_clk,
        ap_rst_n,
        ap_start,
        ap_done,
        ap_idle,
        ap_ready,
        In_V_dout,
        In_V_empty_n,
        In_V_read,
        Out_V_din,
        Out_V_full_n,
        Out_V_write,
        io_In_peek,
        io_In_count,
        io_Out_size,
        io_Out_count,
        ap_return
);

parameter    ap_ST_fsm_state1 = 1'd1;

input   ap_clk;
input   ap_rst_n;
input   ap_start;
output   ap_done;
output   ap_idle;
output   ap_ready;
input  [31:0] In_V_dout;
input   In_V_empty_n;
output   In_V_read;
output  [31:0] Out_V_din;
input   Out_V_full_n;
output   Out_V_write;
input  [31:0] io_In_peek;
input  [31:0] io_In_count;
input  [31:0] io_Out_size;
input  [31:0] io_Out_count;
output  [31:0] ap_return;

reg ap_done;
reg ap_idle;
reg ap_ready;
reg In_V_read;
reg Out_V_write;

 reg    ap_rst_n_inv;
(* fsm_encoding = "none" *) reg   [0:0] ap_CS_fsm;
wire    ap_CS_fsm_state1;
reg   [1:0] ap_phi_mux_p_0_i_phi_fu_76_p4;
wire   [0:0] tmp_nbreadreq_fu_44_p3;
wire   [0:0] tmp_1_nbwritereq_fu_52_p3;
reg   [0:0] ap_NS_fsm;

// power-on initialization
initial begin
#0 ap_CS_fsm = 1'd1;
end

always @ (posedge ap_clk) begin
    if (ap_rst_n_inv == 1'b1) begin
        ap_CS_fsm <= ap_ST_fsm_state1;
    end else begin
        ap_CS_fsm <= ap_NS_fsm;
    end
end

always @ (*) begin
    if (((ap_start == 1'b1) & (tmp_1_nbwritereq_fu_52_p3 == 1'd1) & (tmp_nbreadreq_fu_44_p3 == 1'd1) & (1'b1 == In_V_empty_n) & (1'b1 == ap_CS_fsm_state1))) begin
        In_V_read = 1'b1;
    end else begin
        In_V_read = 1'b0;
    end
end

always @ (*) begin
    if (((ap_start == 1'b1) & (tmp_1_nbwritereq_fu_52_p3 == 1'd1) & (tmp_nbreadreq_fu_44_p3 == 1'd1) & (1'b1 == Out_V_full_n) & (1'b1 == ap_CS_fsm_state1))) begin
        Out_V_write = 1'b1;
    end else begin
        Out_V_write = 1'b0;
    end
end

always @ (*) begin
    if (((ap_start == 1'b1) & (1'b1 == ap_CS_fsm_state1))) begin
        ap_done = 1'b1;
    end else begin
        ap_done = 1'b0;
    end
end

always @ (*) begin
    if (((ap_start == 1'b0) & (1'b1 == ap_CS_fsm_state1))) begin
        ap_idle = 1'b1;
    end else begin
        ap_idle = 1'b0;
    end
end

always @ (*) begin
    if ((1'b1 == ap_CS_fsm_state1)) begin
        if (((tmp_1_nbwritereq_fu_52_p3 == 1'd0) | (tmp_nbreadreq_fu_44_p3 == 1'd0))) begin
            ap_phi_mux_p_0_i_phi_fu_76_p4 = 2'd1;
        end else if (((tmp_1_nbwritereq_fu_52_p3 == 1'd1) & (tmp_nbreadreq_fu_44_p3 == 1'd1))) begin
            ap_phi_mux_p_0_i_phi_fu_76_p4 = 2'd3;
        end else begin
            ap_phi_mux_p_0_i_phi_fu_76_p4 = 'bx;
        end
    end else begin
        ap_phi_mux_p_0_i_phi_fu_76_p4 = 'bx;
    end
end

always @ (*) begin
    if (((ap_start == 1'b1) & (1'b1 == ap_CS_fsm_state1))) begin
        ap_ready = 1'b1;
    end else begin
        ap_ready = 1'b0;
    end
end

always @ (*) begin
    case (ap_CS_fsm)
        ap_ST_fsm_state1 : begin
            ap_NS_fsm = ap_ST_fsm_state1;
        end
        default : begin
            ap_NS_fsm = 'bx;
        end
    endcase
end

assign Out_V_din = In_V_dout;

assign ap_CS_fsm_state1 = ap_CS_fsm[32'd0];

assign ap_return = ap_phi_mux_p_0_i_phi_fu_76_p4;

always @ (*) begin
    ap_rst_n_inv = ~ap_rst_n;
end

assign tmp_1_nbwritereq_fu_52_p3 = Out_V_full_n;

assign tmp_nbreadreq_fu_44_p3 = In_V_empty_n;

endmodule //pass