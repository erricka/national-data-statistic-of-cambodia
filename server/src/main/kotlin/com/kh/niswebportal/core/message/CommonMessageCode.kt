package com.kh.niswebportal.core.message

enum class CommonMessageCode(
    override val value: String,
    override val description: String,
    override val km: String = description,
    override val ko: String = description,
) : ResponseMessageCode {
    SUCCESS("000000", "Success", km = "ជោគជ័យ", ko = "성공"),
    GENERAL_ERROR("000001", "Internal Server Error", km = "កំហុសម៉ាស៊ីនមេខាងក្នុង", ko = "내부 서버 오류"),
    INVALID_DATA("000002", "Invalid Data", km = "ទិន្នន័យមិនត្រឹមត្រូវ", ko = "잘못된 데이터"),
    MISSING_FIELDS("000003", "Missing Require Fields", km = "បំពេញព័ត៌មានដែលត្រូវការ", ko = "필수 항목 누락"),
    AMOUNT_CANNOT_ZERO("000004", "Transaction Amount Cannot Zero", km = "ចំនួនទឹកប្រាក់ប្រតិបត្តិការមិនអាចជាសូន្យ", ko = "거래 금액은 0일 수 없습니다"),
    INVALID_FEE_AMOUNT("000005", "Invalid Fee Amount", km = "ចំនួនថ្លៃសេវាមិនត្រឹមត្រូវ", ko = "잘못된 수수료 금액"),
    TRANSACTION_PROCESS_ERROR("000006", "Transaction Processing Error", km = "កំហុសក្នុងការដំណើរការប្រតិបត្តិការ", ko = "거래 처리 오류"),
    CBC_ADDRESS_NOT_FOUND("000007", "CBC Address not found", km = "រកមិនឃើញអាសយដ្ឋាន CBC", ko = "CBC 주소를 찾을 수 없습니다"),
    INVALID_SIGNATURE("000008", "Invalid signature", km = "ហត្ថលេខាមិនត្រឹមត្រូវ", ko = "잘못된 서명"),
    INVALID_FILE_IMAGE("000009", "Invalid image file", km = "ឯកសាររូបភាពមិនត្រឹមត្រូវ", ko = "잘못된 이미지 파일"),
    INVALID_FILE_PDF("000010", "Invalid pdf file", km = "ឯកសារ PDF មិនត្រឹមត្រូវ", ko = "잘못된 PDF 파일"),
    DUPLICATION_TRANSACTION_ID("000011", "Duplicated Transaction", km = "ប្រតិបត្តិការស្ទួន", ko = "중복된 거래"),
    MCI_ERROR("000011", "Internal Server Error-[MCI]", km = "កំហុសម៉ាស៊ីនមេខាងក្នុង-[MCI]", ko = "내부 서버 오류-[MCI]"),
    ACCOUNT_DISABLED("999995", "Account is disabled", km = " គណនីត្រូវបានបិទ", ko = "계정이 비활성화되었습니다"),
    SOMEONE_LOOGGED_IN("999996", "Someone has logged in", km = "មាននរណាម្នាក់បានចូលគណនី", ko = "다른 사용자가 로그인했습니다"),
    CIPHER_FAILED("999997", "Failed do cipher process", km = "ដំណើរការអ៊ិនគ្រីបបរាជ័យ", ko = "암호화 처리 실패"),
    ACCESS_DENIED("999998", "Access Denied", km = "ការចូលប្រើត្រូវបានបដិសេធ", ko = "접근 거부"),
    UNAUTHORIZED("999993", "UnAuthorized", km = "មិនមានសិទ្ធិ", ko = "권한 없음"),
}