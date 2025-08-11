// sim_c/sim.cc
#include "include/simulator.h"
#include <verilated.h>
#include "Vtop.h"
#ifdef VCD
    #include "verilated_vcd_c.h"
#endif

#include <iostream>
#include <bitset>

using namespace std; 

// ===================================================================
// Simulator 类实现
// ===================================================================

Simulator::Simulator(int argc, char* argv[]) {
    contextp_ = make_unique<VerilatedContext>();
    contextp_->commandArgs(argc, argv);
    top_ = make_unique<Vtop>(contextp_.get());

#ifdef VCD
    init_vcd();
#endif
}

Simulator::~Simulator() {
#ifdef VCD
    if (tfp_) {
        tfp_->close();
    }
#endif
}

void Simulator::init_vcd() {
#ifdef VCD
    contextp_->traceEverOn(true);
    tfp_ = new VerilatedVcdC;
    top_->trace(tfp_, 99);
    tfp_->open("build/vfpu/top.vcd");
#endif
}

void Simulator::single_cycle() {
    top_->clock = 0;
    top_->eval();
#ifdef VCD
    if (tfp_) {
        tfp_->dump(contextp_->time());
    }
#endif
    contextp_->timeInc(1);

    top_->clock = 1;
    top_->eval();
#ifdef VCD
    if (tfp_) {
        tfp_->dump(contextp_->time());
    }
#endif
    contextp_->timeInc(1);
}

void Simulator::reset(int n) {
    top_->reset = 1;
    for (int i = 0; i < n; i++) {
        single_cycle();
    }
    top_->reset = 0;
    top_->eval();
}

bool Simulator::run_test(const TestCase& test) {
    test.print_details();

    // -- 执行仿真 --
    // 复位DUT
    reset(2);

    // 1. 设置控制信号和数据输入
    top_->io_valid_in = 1;
    top_->io_is_fp32  = test.is_fp32;
    top_->io_is_fp16  = test.is_fp16;
    top_->io_is_bf16  = test.is_bf16;
    top_->io_is_widen = test.is_widen;
    top_->io_a_already_widen = 0; // 新增信号连接，设为0

    // 2. 根据模式设置数据输入端口
    switch(test.mode) {
        case TestMode::FP32:
            top_->io_a_in_32 = test.a_fp32_bits;
            top_->io_b_in_32 = test.b_fp32_bits;
            break;
        case TestMode::FP16:
            // 注意：Verilator会把 a_in_16: Vec(2, UInt(16.W)) 转换成 io_a_in_16_0, io_a_in_16_1
            top_->io_a_in_16_0 = test.a1_fp16_bits;
            top_->io_b_in_16_0 = test.b1_fp16_bits;
            top_->io_a_in_16_1 = test.a2_fp16_bits;
            top_->io_b_in_16_1 = test.b2_fp16_bits;
            break;
        case TestMode::BF16:
            // BF16也使用16位端口
            top_->io_a_in_16_0 = test.a1_bf16_bits;
            top_->io_b_in_16_0 = test.b1_bf16_bits;
            top_->io_a_in_16_1 = test.a2_bf16_bits;
            top_->io_b_in_16_1 = test.b2_bf16_bits;
            break;
        case TestMode::FP16_Widen:
            // FP16 Widen: a,b是FP16, result是FP32
            // 在test_case中，a,b的fp16值被存在了a_fp32_bits和b_fp32_bits的高16位中
            top_->io_a_in_16_0 = 0;
            top_->io_a_in_16_1 = (test.a_fp32_bits >> 16) & 0xFFFF;
            top_->io_b_in_16_0 = 0;
            top_->io_b_in_16_1 = (test.b_fp32_bits >> 16) & 0xFFFF;
            break;
        case TestMode::BF16_Widen:
            // BF16 Widen: a,b是BF16, result是FP32
            // 在test_case中，a,b的bf16值被存在了a_fp32_bits和b_fp32_bits的高16位中
            top_->io_a_in_16_0 = 0;
            top_->io_a_in_16_1 = (test.a_fp32_bits >> 16) & 0xFFFF;
            top_->io_b_in_16_0 = 0;
            top_->io_b_in_16_1 = (test.b_fp32_bits >> 16) & 0xFFFF;
            break;
    }

    // 输入有效，等待一个周期，让DUT接收数据
    single_cycle();
    // 输入无效
    top_->io_valid_in = 0;

    // -- 等待DUT的valid_out信号，或超时 --
    int timeout = 100; // 设置超时周期
    while (!top_->io_valid_out && timeout > 0) {
        single_cycle();
        timeout--;
    }

    // -- 获取DUT输出并检查结果 --
    if (top_->io_valid_out) {
        DutOutputs dut_res;
        dut_res.res_out_32 = top_->io_res_out_32;
        dut_res.res_out_16_0 = top_->io_res_out_16_0;
        dut_res.res_out_16_1 = top_->io_res_out_16_1;
        bool result = test.check_result(dut_res);
        
        // 如果测试失败，多跑一个周期来记录更多波形信息
        if (!result) {
            printf("Test failed! Running one more cycle for better waveform debugging...\n");
            single_cycle();
        }
        
        return result;
    } else {
        printf("Timeout waiting for valid_out\n");
        return false;
    }
}