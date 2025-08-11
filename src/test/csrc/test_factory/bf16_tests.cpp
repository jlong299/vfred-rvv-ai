#include "../include/test_factory.h"
#include "../include/fp_utils.h"
#include <vector>
#include <cstdio>

void add_bf16_tests(std::vector<TestCase>& tests) {
    // -- BF16 并行双路半精度浮点数测试 --
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x3f80, 0x4000}, FADD_Operands_Hex_BF16{0x4040, 0x3f80}, ErrorType::Precise)); // 1.0 + 2.0 = 3.0 | 3.0 + 1.0 = 4.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0xbf80, 0x4000}, FADD_Operands_Hex_BF16{0x3f80, 0xc000}, ErrorType::Precise)); // -1.0 + 2.0 = 1.0 | 1.0 + -2.0 = -1.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x3f80, 0xbf80}, FADD_Operands_Hex_BF16{0x4080, 0x3f00}, ErrorType::Precise)); // 1.0 + -1.0 = 0.0 | 4.0 + 0.5 = 4.5
    // 零值测试
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x0000, 0x4000}, FADD_Operands_Hex_BF16{0x4000, 0x0000}, ErrorType::Precise)); // 0.0 + 2.0 = 2.0 | 2.0 + 0.0 = 2.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x3f80, 0x0000}, FADD_Operands_Hex_BF16{0x0000, 0x4040}, ErrorType::Precise)); // 1.0 + 0.0 = 1.0 | 0.0 + 3.0 = 3.0
    // 无穷大测试
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x7f80, 0x4000}, FADD_Operands_Hex_BF16{0x3f80, 0xff80}, ErrorType::Precise)); // +inf + 2.0 = +inf | 1.0 + -inf = -inf
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x3f80, 0x7f80}, FADD_Operands_Hex_BF16{0x7f80, 0x7f80}, ErrorType::Precise)); // 1.0 + +inf = +inf | +inf + inf = +inf
    // 非规格化数测试（BF16的非规格化数非常小）
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x0001, 0x4000}, FADD_Operands_Hex_BF16{0x007f, 0x3f80}, ErrorType::Precise)); // 最小非规格化数 + 2.0 | 最大非规格化数 + 1.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x3f80, 0x0001}, FADD_Operands_Hex_BF16{0x4000, 0x007f}, ErrorType::Precise)); // 1.0 + 最小非规格化数 | 2.0 + 最大非规格化数
    // 边界值测试
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x7f7f, 0x3f80}, FADD_Operands_Hex_BF16{0x0080, 0x3f80}, ErrorType::Precise)); // 最大规格化数 + 1.0 | 最小规格化数 + 1.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0xff7f, 0x3f80}, FADD_Operands_Hex_BF16{0x8080, 0x3f80}, ErrorType::Precise)); // -最大规格化数 + 1.0 | -最小规格化数 + 1.0
    // 接近溢出的测试
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x7f00, 0x4000}, FADD_Operands_Hex_BF16{0x7e80, 0x4040}, ErrorType::Precise)); // 大数 + 2.0 | 大数 + 3.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x7f7f, 0x3f00}, FADD_Operands_Hex_BF16{0x7e80, 0x3f00}, ErrorType::Precise)); // 最大数 + 0.5 | 大数 + 0.5
    // 精度损失边界测试  
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x4000, 0x3e80}, FADD_Operands_Hex_BF16{0x4040, 0x3e00}, ErrorType::Precise)); // 2.0 + 小数 | 3.0 + 小数
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x5000, 0x3d80}, FADD_Operands_Hex_BF16{0x4c80, 0x3d00}, ErrorType::Precise)); // 大数 + 极小数 | 中数 + 极小数
    // 符号组合的复杂测试
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0xc000, 0xc000}, FADD_Operands_Hex_BF16{0xc040, 0xc000}, ErrorType::Precise)); // (-2.0) + (-2.0) = -4.0 | (-3.0) + (-2.0) = -5.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x4000, 0xc000}, FADD_Operands_Hex_BF16{0x4040, 0xbf80}, ErrorType::Precise)); // 2.0 + (-2.0) = 0.0 | 3.0 + (-1.0) = 2.0
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0xc000, 0x4000}, FADD_Operands_Hex_BF16{0xc040, 0x4000}, ErrorType::Precise)); // (-2.0) + 2.0 = 0.0 | (-3.0) + 2.0 = -1.0
    // 其他复杂测试用例
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x42a5, 0x3e12}, FADD_Operands_Hex_BF16{0x4567, 0x3d89}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x4012, 0x4234}, FADD_Operands_Hex_BF16{0x4156, 0x3e78}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x9a1d, 0x1fa1}, FADD_Operands_Hex_BF16{0xa174, 0xcafa}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x80e1, 0x80ed}, FADD_Operands_Hex_BF16{0x80cd, 0x806d}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x80e1, 0x80ed}, FADD_Operands_Hex_BF16{0x80cd, 0x806d}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0xbf80, 0x0200}, FADD_Operands_Hex_BF16{0xbf80, 0x0200}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0x0f00, 0x80cf}, FADD_Operands_Hex_BF16{0x0f00, 0x80cf}, ErrorType::Precise));
    tests.push_back(TestCase(FADD_Operands_Hex_BF16{0xb0f, 0xf7f}, FADD_Operands_Hex_BF16{0xb0f, 0xf7f}, ErrorType::Precise));

    printf("\n---- Random tests for BF16 ----\n");
    int num_random_tests_bf16 = 200;
    ErrorType default_error_type = ErrorType::Precise;
    // ---- BF16 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_any_bf16(), gen_any_bf16()};
        FADD_Operands_Hex_BF16 ops2 = {gen_any_bf16(), gen_any_bf16()};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    
    // ---- 进行不同指数范围的BF16随机测试 ----
    // 小数范围测试：指数[-50, -10]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-50, -10), gen_random_bf16(-50, -10)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-50, -10), gen_random_bf16(-50, -10)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 中等数值范围测试：指数[-10, 10]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-10, 10), gen_random_bf16(-10, 10)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-10, 10), gen_random_bf16(-10, 10)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 大数范围测试：指数[10, 50]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(10, 50), gen_random_bf16(10, 50)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(10, 50), gen_random_bf16(10, 50)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 极端范围测试：指数[-126, 127]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, 127), gen_random_bf16(-126, 127)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, 127), gen_random_bf16(-126, 127)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 非规格化数边界测试：指数[-126, -125]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, -125), gen_random_bf16(-126, 20)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 混合精度范围测试
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 高精度范围测试
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, -125), gen_random_bf16(-126, -125)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, -125), gen_random_bf16(-126, -125)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 全范围混合测试
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-127, 10), gen_random_bf16(-127, 10)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-127, 10), gen_random_bf16(-127, 10)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 相对误差测试（较高精度要求）
    for (int i = 0; i < num_random_tests_bf16 / 5; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-20, 20), gen_random_bf16(-20, 20)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-20, 20), gen_random_bf16(-20, 20)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
    // 极端范围测试：指数[-127, -126]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FADD_Operands_Hex_BF16 ops1 = {gen_random_bf16(-127, -126), gen_random_bf16(-127, -126)};
        FADD_Operands_Hex_BF16 ops2 = {gen_random_bf16(-127, -126), gen_random_bf16(-127, -126)};
        tests.push_back(TestCase(ops1, ops2, default_error_type));
    }
} 