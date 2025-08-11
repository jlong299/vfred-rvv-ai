#ifndef __TEST_FACTORY_H__
#define __TEST_FACTORY_H__

#include <vector>
#include "test_case.h"

// Creates and returns a vector of all test cases.
std::vector<TestCase> create_all_tests();

// Declarations for split test functions
void add_fp32_tests(std::vector<TestCase>& tests);
void add_fp16_tests(std::vector<TestCase>& tests);
void add_bf16_tests(std::vector<TestCase>& tests);
void add_fp16_widen_tests(std::vector<TestCase>& tests);
void add_bf16_widen_tests(std::vector<TestCase>& tests);

#endif // __TEST_FACTORY_H__ 