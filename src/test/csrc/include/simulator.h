#ifndef __SIMULATOR_H__
#define __SIMULATOR_H__

#include <memory>
#include "test_case.h"

// 前向声明Verilator相关类
class Vtop;
class VerilatedContext;

#ifdef VCD
class VerilatedVcdC;
#endif

// ===================================================================
// Simulator 类: 封装Verilator仿真控制
// ===================================================================
class Simulator {
public:
    Simulator(int argc, char* argv[]);
    ~Simulator();

    bool run_test(const TestCase& test);
    void reset(int n);

private:
    void init_vcd();
    void single_cycle();
    
    // Verilator核心对象
    std::unique_ptr<VerilatedContext> contextp_;
    std::unique_ptr<Vtop> top_;

    // VCD波形跟踪器
#ifdef VCD
    VerilatedVcdC* tfp_ = nullptr;
#endif
};

#endif // __SIMULATOR_H__