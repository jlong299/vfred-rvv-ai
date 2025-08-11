#ifndef __TEST_CASE_H__
#define __TEST_CASE_H__

#include <cstdint>

#include "fp_utils.h"

// 定义FADD操作数结构体
struct FADD_Operands {
    float a, b;
};

// 定义16进制FADD操作数结构体
struct FADD_Operands_Hex {
    uint32_t a_hex, b_hex;
};
struct FADD_Operands_Hex_16 {
    uint16_t a_hex, b_hex;
};
struct FADD_Operands_Hex_BF16 {
    uint16_t a_hex, b_hex;
};
struct FADD_Operands_FP16_Widen {
    uint16_t a_hex, b_hex;
};
struct FADD_Operands_BF16_Widen {
    uint16_t a_hex, b_hex;
};

// 定义测试模式的枚举类型
enum class TestMode {
    FP32,
    FP16,
    BF16,
    FP16_Widen,
    BF16_Widen
};

// 定义测试结果允许误差范围
enum class ErrorType {
    Precise,
    ULP, //允许若干 ulp (unit in the last place) 的误差
    RelativeError, // 相对误差
    ULP_or_RelativeError // 允许若干 ulp 或 相对误差
};

// 用于从仿真器传递DUT输出到TestCase进行检查的结构体
struct DutOutputs {
    uint32_t res_out_32;
    uint16_t res_out_16_0;
    uint16_t res_out_16_1;
};

// ===================================================================
// TestCase 类: 封装单个测试用例
// ===================================================================
class TestCase {
public:
    // 构造函数 for FP32 single operation using hexadecimal input
    TestCase(const FADD_Operands_Hex& ops_hex, ErrorType error_type = ErrorType::ULP);
    
    // 构造函数 for FP16 dual operation using hexadecimal input
    TestCase(const FADD_Operands_Hex_16& op1, const FADD_Operands_Hex_16& op2, ErrorType error_type = ErrorType::ULP);
    
    // 构造函数 for BF16 dual operation using hexadecimal input
    TestCase(const FADD_Operands_Hex_BF16& op1, const FADD_Operands_Hex_BF16& op2, ErrorType error_type = ErrorType::ULP);
    
    // 构造函数 for FP16 widen operation using hexadecimal input (a,b are FP16, result is FP32)
    TestCase(const FADD_Operands_FP16_Widen& ops_widen, ErrorType error_type = ErrorType::ULP);
    
    // 构造函数 for BF16 widen operation using hexadecimal input (a,b are BF16, result is FP32)
    TestCase(const FADD_Operands_BF16_Widen& ops_widen, ErrorType error_type = ErrorType::ULP);
    
    void print_details() const;
    bool check_result(const DutOutputs& dut_res) const;

    TestMode mode;
    ErrorType error_type;

    // --- 公共数据成员，供 Simulator 直接访问 ---
    // 控制信号
    bool is_fp32, is_fp16, is_bf16, is_widen;

    // FP32 模式数据
    uint32_t a_fp32_bits, b_fp32_bits;

    // FP16 模式数据
    uint16_t a1_fp16_bits, b1_fp16_bits;
    uint16_t a2_fp16_bits, b2_fp16_bits;

    // BF16 模式数据
    uint16_t a1_bf16_bits, b1_bf16_bits;
    uint16_t a2_bf16_bits, b2_bf16_bits;

private:
    // 原始浮点数值，用于打印和计算期望结果
    // FP32 - now using the struct for consistency
    FADD_Operands op_fp;
    // FP16 - uses the struct
    FADD_Operands op1_fp, op2_fp;

    // 期望结果
    uint32_t expected_res_fp32;
    uint16_t expected_res1_fp16, expected_res2_fp16;
    uint16_t expected_res1_bf16, expected_res2_bf16;
};

#endif // __TEST_CASE_H__ 