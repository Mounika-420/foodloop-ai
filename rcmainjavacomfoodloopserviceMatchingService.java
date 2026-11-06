 .gitignore                                         |   1 [32m+[m
 backend/0)                                         |   0
 backend/i.getArgument(0))                          |   0
 backend/run.log                                    | 181 [31m-------------[m
 .../java/com/foodloop/config/SecurityConfig.java   |   2 [32m+[m
 .../com/foodloop/controller/AdminController.java   |  78 [32m++++++[m
 .../foodloop/controller/DonationController.java    |  78 [32m++++++[m
 .../com/foodloop/controller/MatchController.java   |  60 [32m+++++[m
 .../java/com/foodloop/dto/AdminUserResponse.java   |  17 [32m++[m
 .../java/com/foodloop/dto/DonationRequest.java     |  43 [32m+++[m
 .../java/com/foodloop/dto/DonationResponse.java    |  38 [32m+++[m
 .../main/java/com/foodloop/dto/MatchResponse.java  |  20 [32m++[m
 .../java/com/foodloop/dto/NgoOfferResponse.java    |  15 [32m++[m
 .../main/java/com/foodloop/dto/PagedResult.java    |  12 [32m+[m
 .../java/com/foodloop/dto/RejectMatchRequest.java  |   6 [32m+[m
 .../java/com/foodloop/dto/StatusChangeRequest.java |  10 [32m+[m
 .../foodloop/dto/VerificationDecisionRequest.java  |   6 [32m+[m
 .../com/foodloop/dto/VerificationResponse.java     |  16 [32m++[m
 .../java/com/foodloop/mapper/DonationMapper.java   |  40 [32m+++[m
 .../foodloop/repository/DonationRepository.java    |  28 [32m+[m[31m-[m
 .../com/foodloop/repository/UserRepository.java    |  14 [32m+[m[31m-[m
 .../java/com/foodloop/service/AdminService.java    | 215 [32m+++++++++++++++[m
 .../java/com/foodloop/service/DonationService.java | 256 [32m++++++++++++++++++[m
 .../java/com/foodloop/service/MatchingService.java | 292 [32m+++++++++++++++++++++[m
 .../com/foodloop/service/RuleBasedMatcher.java     | 137 [32m++++++++++[m
 .../com/foodloop/util/DonationCodeGenerator.java   |  20 [32m++[m
 .../main/java/com/foodloop/util/UnitConverter.java |  27 [32m+[m[31m-[m
 .../com/foodloop/service/AdminServiceTest.java     | 175 [32m++++++++++++[m
 .../com/foodloop/service/DonationServiceTest.java  | 226 [32m++++++++++++++++[m
 .../foodloop/util/DonationCodeGeneratorTest.java   |  27 [32m++[m
 .../java/com/foodloop/util/UnitConverterTest.java  |  34 [32m+++[m
 31 files changed, 1886 insertions(+), 188 deletions(-)
