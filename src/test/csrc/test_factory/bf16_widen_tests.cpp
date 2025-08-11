#include "../include/test_factory.h"
#include "../include/fp_utils.h"
#include <vector>
#include <cstdio>

void add_bf16_widen_tests(std::vector<TestCase>& tests) {
    // -- BF16 widen 测试 --
    tests.push_back(TestCase(FADD_Operands_BF16_Widen{0x3f80, 0x4000}, ErrorType::Precise)); // 1.0 + 2.0 = 3.0
    tests.push_back(TestCase(FADD_Operands_BF16_Widen{0xbf80, 0x4000}, ErrorType::Precise)); // -1.0 + 2.0 = 1.0
    tests.push_back(TestCase(FADD_Operands_BF16_Widen{0x3f80, 0xbf80}, ErrorType::Precise)); // 1.0 + -1.0 = 0.0
    tests.push_back(TestCase(FADD_Operands_BF16_Widen{0x0000, 0x4000}, ErrorType::Precise)); // 0.0 + 2.0 = 2.0

    printf("\n---- Random tests for BF16 Widen ----\n");
    int num_random_tests_bf16_widen = 200;
    ErrorType default_error_type = ErrorType::Precise;
    // ---- BF16 widen 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_any_bf16(), gen_any_bf16()};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 更多不同范围的随机测试...
    // 正常范围测试
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-10, 10), gen_random_bf16(-10, 10)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 小数范围测试 - BF16指数范围
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-50, -10), gen_random_bf16(-50, -10)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 大数范围测试 - BF16指数范围  
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(10, 50), gen_random_bf16(10, 50)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 混合指数范围测试
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-126, 127), gen_random_bf16(-126, 127)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 非规格化数边界测试 - BF16
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-126, -125), gen_random_bf16(-126, 20)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 全范围随机测试 - 最全面的测试
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-127, 127), gen_random_bf16(-127, 127)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    // 特殊组合测试 - 一个操作数极大，另一个极小
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(50, 100), gen_random_bf16(-100, -50)};
        tests.push_back(TestCase(ops, default_error_type));
    }
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FADD_Operands_BF16_Widen ops = {gen_random_bf16(-100, -50), gen_random_bf16(50, 100)};
        tests.push_back(TestCase(ops, default_error_type));
    }
} 