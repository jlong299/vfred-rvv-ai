#include "include/simulator.h"
#include "include/test_factory.h"
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <ctime>

int main(int argc, char *argv[]) {
  // 1. 初始化随机数生成器种子
  srand(time(NULL)); 

  // 2. 初始化仿真器
  Simulator sim(argc, argv);

  // 3. 使用 TestFactory 创建所有测试用例
  printf("--- Creating all test cases ---\n");
  std::vector<TestCase> tests = create_all_tests();
  printf("--- All test cases created ---\n\n");

  // 4. 执行所有测试，遇到错误即停止
  for (size_t i = 0; i < tests.size(); ++i) {
    printf("--- Running test case %zu of %zu ---\n", i + 1, tests.size());
    if (!sim.run_test(tests[i])) {
      printf("\n=================================\n");
      printf("      TEST FAILED!\n");
      printf("=================================\n");
      printf("Failed on test case %zu.\n", i + 1);
      return 1; // 返回非零值表示失败
    }
  }

  // 5. 如果所有测试都通过，打印成功信息
  printf("\n=================================\n");
  printf("      ALL TESTS PASSED!\n");
  printf("=================================\n");
  printf("Successfully completed %zu test cases.\n", tests.size());
  printf("=================================\n");

  return 0; // 返回0表示成功
}