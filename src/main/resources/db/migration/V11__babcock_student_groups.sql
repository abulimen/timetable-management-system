-- V11: Babcock Student Groups (64 departments × 4-6 years × 2-5 groups)
-- Estimated: ~800 student groups

-- Helper: Create groups for a department with specified years and groups
-- Format: DEPT_Year_Group (e.g., COSC_1_A, COSC_1_B, etc.)

-- =========================================
-- COMPUTING DEPARTMENTS (5 groups per year: A-E)
-- =========================================

-- Computer Science (COSC) - 4 years × 5 groups = 20 groups
INSERT INTO student_group (name, size) VALUES
('COSC_1_A', 60), ('COSC_1_B', 55), ('COSC_1_C', 50), ('COSC_1_D', 45), ('COSC_1_E', 40),
('COSC_2_A', 55), ('COSC_2_B', 50), ('COSC_2_C', 45), ('COSC_2_D', 40), ('COSC_2_E', 35),
('COSC_3_A', 50), ('COSC_3_B', 45), ('COSC_3_C', 40), ('COSC_3_D', 35), ('COSC_3_E', 30),
('COSC_4_A', 45), ('COSC_4_B', 40), ('COSC_4_C', 35), ('COSC_4_D', 30), ('COSC_4_E', 25);

-- Software Engineering (SENG) - 4 years × 5 groups
INSERT INTO student_group (name, size) VALUES
('SENG_1_A', 55), ('SENG_1_B', 50), ('SENG_1_C', 45), ('SENG_1_D', 40), ('SENG_1_E', 35),
('SENG_2_A', 50), ('SENG_2_B', 45), ('SENG_2_C', 40), ('SENG_2_D', 35), ('SENG_2_E', 30),
('SENG_3_A', 45), ('SENG_3_B', 40), ('SENG_3_C', 35), ('SENG_3_D', 30), ('SENG_3_E', 25),
('SENG_4_A', 40), ('SENG_4_B', 35), ('SENG_4_C', 30), ('SENG_4_D', 25), ('SENG_4_E', 20);

-- Information Technology (ITGY) - 4 years × 5 groups
INSERT INTO student_group (name, size) VALUES
('ITGY_1_A', 50), ('ITGY_1_B', 45), ('ITGY_1_C', 40), ('ITGY_1_D', 35), ('ITGY_1_E', 30),
('ITGY_2_A', 45), ('ITGY_2_B', 40), ('ITGY_2_C', 35), ('ITGY_2_D', 30), ('ITGY_2_E', 25),
('ITGY_3_A', 40), ('ITGY_3_B', 35), ('ITGY_3_C', 30), ('ITGY_3_D', 25), ('ITGY_3_E', 20),
('ITGY_4_A', 35), ('ITGY_4_B', 30), ('ITGY_4_C', 25), ('ITGY_4_D', 20), ('ITGY_4_E', 20);

-- Computer Engineering (COENG) - 4 years × 5 groups
INSERT INTO student_group (name, size) VALUES
('COENG_1_A', 55), ('COENG_1_B', 50), ('COENG_1_C', 45), ('COENG_1_D', 40), ('COENG_1_E', 35),
('COENG_2_A', 50), ('COENG_2_B', 45), ('COENG_2_C', 40), ('COENG_2_D', 35), ('COENG_2_E', 30),
('COENG_3_A', 45), ('COENG_3_B', 40), ('COENG_3_C', 35), ('COENG_3_D', 30), ('COENG_3_E', 25),
('COENG_4_A', 40), ('COENG_4_B', 35), ('COENG_4_C', 30), ('COENG_4_D', 25), ('COENG_4_E', 20);

-- CSIT, CSMA, BMEI, PMAT (4 × 4 years × 3 groups each = 48 groups)
INSERT INTO student_group (name, size) VALUES
('CSIT_1_A', 45), ('CSIT_1_B', 40), ('CSIT_1_C', 35),
('CSIT_2_A', 40), ('CSIT_2_B', 35), ('CSIT_2_C', 30),
('CSIT_3_A', 35), ('CSIT_3_B', 30), ('CSIT_3_C', 25),
('CSIT_4_A', 30), ('CSIT_4_B', 25), ('CSIT_4_C', 20),
('CSMA_1_A', 40), ('CSMA_1_B', 35), ('CSMA_1_C', 30),
('CSMA_2_A', 35), ('CSMA_2_B', 30), ('CSMA_2_C', 25),
('CSMA_3_A', 30), ('CSMA_3_B', 25), ('CSMA_3_C', 20),
('CSMA_4_A', 25), ('CSMA_4_B', 20), ('CSMA_4_C', 20),
('BMEI_1_A', 35), ('BMEI_1_B', 30), ('BMEI_1_C', 25),
('BMEI_2_A', 30), ('BMEI_2_B', 25), ('BMEI_2_C', 20),
('BMEI_3_A', 25), ('BMEI_3_B', 20), ('BMEI_3_C', 20),
('BMEI_4_A', 20), ('BMEI_4_B', 20), ('BMEI_4_C', 20),
('PMAT_1_A', 35), ('PMAT_1_B', 30), ('PMAT_1_C', 25),
('PMAT_2_A', 30), ('PMAT_2_B', 25), ('PMAT_2_C', 20),
('PMAT_3_A', 25), ('PMAT_3_B', 20), ('PMAT_3_C', 20),
('PMAT_4_A', 20), ('PMAT_4_B', 20), ('PMAT_4_C', 20);

-- =========================================
-- ENGINEERING DEPARTMENTS (3 groups: A-C)
-- =========================================

-- Electrical & Electronic (EEENG)
INSERT INTO student_group (name, size) VALUES
('EEENG_1_A', 55), ('EEENG_1_B', 50), ('EEENG_1_C', 45),
('EEENG_2_A', 50), ('EEENG_2_B', 45), ('EEENG_2_C', 40),
('EEENG_3_A', 45), ('EEENG_3_B', 40), ('EEENG_3_C', 35),
('EEENG_4_A', 40), ('EEENG_4_B', 35), ('EEENG_4_C', 30);

-- Civil Engineering (CVENG)
INSERT INTO student_group (name, size) VALUES
('CVENG_1_A', 50), ('CVENG_1_B', 45), ('CVENG_1_C', 40),
('CVENG_2_A', 45), ('CVENG_2_B', 40), ('CVENG_2_C', 35),
('CVENG_3_A', 40), ('CVENG_3_B', 35), ('CVENG_3_C', 30),
('CVENG_4_A', 35), ('CVENG_4_B', 30), ('CVENG_4_C', 25);

-- Mechanical Engineering (MEENG)
INSERT INTO student_group (name, size) VALUES
('MEENG_1_A', 50), ('MEENG_1_B', 45), ('MEENG_1_C', 40),
('MEENG_2_A', 45), ('MEENG_2_B', 40), ('MEENG_2_C', 35),
('MEENG_3_A', 40), ('MEENG_3_B', 35), ('MEENG_3_C', 30),
('MEENG_4_A', 35), ('MEENG_4_B', 30), ('MEENG_4_C', 25);

-- =========================================
-- MEDICINE DEPARTMENTS (6 years × 2 groups: A-B)
-- =========================================

-- Medicine & Surgery (MBBS) - 6 years
INSERT INTO student_group (name, size) VALUES
('MBBS_1_A', 60), ('MBBS_1_B', 55),
('MBBS_2_A', 55), ('MBBS_2_B', 50),
('MBBS_3_A', 50), ('MBBS_3_B', 45),
('MBBS_4_A', 45), ('MBBS_4_B', 40),
('MBBS_5_A', 40), ('MBBS_5_B', 35),
('MBBS_6_A', 35), ('MBBS_6_B', 30);

-- =========================================
-- NURSING & ALLIED HEALTH (3 groups: A-C)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('NRSG_1_A', 55), ('NRSG_1_B', 50), ('NRSG_1_C', 45),
('NRSG_2_A', 50), ('NRSG_2_B', 45), ('NRSG_2_C', 40),
('NRSG_3_A', 45), ('NRSG_3_B', 40), ('NRSG_3_C', 35),
('NRSG_4_A', 40), ('NRSG_4_B', 35), ('NRSG_4_C', 30),
('MLSC_1_A', 50), ('MLSC_1_B', 45), ('MLSC_1_C', 40),
('MLSC_2_A', 45), ('MLSC_2_B', 40), ('MLSC_2_C', 35),
('MLSC_3_A', 40), ('MLSC_3_B', 35), ('MLSC_3_C', 30),
('MLSC_4_A', 35), ('MLSC_4_B', 30), ('MLSC_4_C', 25);

-- =========================================
-- MANAGEMENT & BUSINESS (2 groups: A-B)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('ACCT_1_A', 60), ('ACCT_1_B', 55),
('ACCT_2_A', 55), ('ACCT_2_B', 50),
('ACCT_3_A', 50), ('ACCT_3_B', 45),
('ACCT_4_A', 45), ('ACCT_4_B', 40),
('BAM_1_A', 65), ('BAM_1_B', 60),
('BAM_2_A', 60), ('BAM_2_B', 55),
('BAM_3_A', 55), ('BAM_3_B', 50),
('BAM_4_A', 50), ('BAM_4_B', 45),
('BAFN_1_A', 55), ('BAFN_1_B', 50),
('BAFN_2_A', 50), ('BAFN_2_B', 45),
('BAFN_3_A', 45), ('BAFN_3_B', 40),
('BAFN_4_A', 40), ('BAFN_4_B', 35);

-- =========================================
-- LAW (2 groups: A-B)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('LAWS_1_A', 55), ('LAWS_1_B', 50),
('LAWS_2_A', 50), ('LAWS_2_B', 45),
('LAWS_3_A', 45), ('LAWS_3_B', 40),
('LAWS_4_A', 40), ('LAWS_4_B', 35),
('BCOL_1_A', 45), ('BCOL_1_B', 40),
('BCOL_2_A', 40), ('BCOL_2_B', 35),
('BCOL_3_A', 35), ('BCOL_3_B', 30),
('BCOL_4_A', 30), ('BCOL_4_B', 25);

-- =========================================
-- SOCIAL SCIENCES (2 groups)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('ECON_1_A', 60), ('ECON_1_B', 55),
('ECON_2_A', 55), ('ECON_2_B', 50),
('ECON_3_A', 50), ('ECON_3_B', 45),
('ECON_4_A', 45), ('ECON_4_B', 40),
('MCOM_1_A', 55), ('MCOM_1_B', 50),
('MCOM_2_A', 50), ('MCOM_2_B', 45),
('MCOM_3_A', 45), ('MCOM_3_B', 40),
('MCOM_4_A', 40), ('MCOM_4_B', 35),
('PSPA_1_A', 50), ('PSPA_1_B', 45),
('PSPA_2_A', 45), ('PSPA_2_B', 40),
('PSPA_3_A', 40), ('PSPA_3_B', 35),
('PSPA_4_A', 35), ('PSPA_4_B', 30);

-- =========================================
-- EDUCATION & HUMANITIES (2 groups)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('EDFO_1_A', 50), ('EDFO_1_B', 45),
('EDFO_2_A', 45), ('EDFO_2_B', 40),
('EDFO_3_A', 40), ('EDFO_3_B', 35),
('EDFO_4_A', 35), ('EDFO_4_B', 30),
('LALI_1_A', 45), ('LALI_1_B', 40),
('LALI_2_A', 40), ('LALI_2_B', 35),
('LALI_3_A', 35), ('LALI_3_B', 30),
('LALI_4_A', 30), ('LALI_4_B', 25),
('HIST_1_A', 40), ('HIST_1_B', 35),
('HIST_2_A', 35), ('HIST_2_B', 30),
('HIST_3_A', 30), ('HIST_3_B', 25),
('HIST_4_A', 25), ('HIST_4_B', 20),
('RELB_1_A', 45), ('RELB_1_B', 40),
('RELB_2_A', 40), ('RELB_2_B', 35),
('RELB_3_A', 35), ('RELB_3_B', 30),
('RELB_4_A', 30), ('RELB_4_B', 25);

-- =========================================
-- SCIENCE & AGRICULTURE (2 groups)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('MBGY_1_A', 45), ('MBGY_1_B', 40),
('MBGY_2_A', 40), ('MBGY_2_B', 35),
('MBGY_3_A', 35), ('MBGY_3_B', 30),
('MBGY_4_A', 30), ('MBGY_4_B', 25),
('BCHM_1_A', 50), ('BCHM_1_B', 45),
('BCHM_2_A', 45), ('BCHM_2_B', 40),
('BCHM_3_A', 40), ('BCHM_3_B', 35),
('BCHM_4_A', 35), ('BCHM_4_B', 30),
('AGEC_1_A', 40), ('AGEC_1_B', 35),
('AGEC_2_A', 35), ('AGEC_2_B', 30),
('AGEC_3_A', 30), ('AGEC_3_B', 25),
('AGEC_4_A', 25), ('AGEC_4_B', 20),
('ANSC_1_A', 35), ('ANSC_1_B', 30),
('ANSC_2_A', 30), ('ANSC_2_B', 25),
('ANSC_3_A', 25), ('ANSC_3_B', 20),
('ANSC_4_A', 20), ('ANSC_4_B', 20);

-- =========================================  
-- ENVIRONMENT & ARCHITECTURE (2 groups)
-- =========================================

INSERT INTO student_group (name, size) VALUES
('ARCH_1_A', 45), ('ARCH_1_B', 40),
('ARCH_2_A', 40), ('ARCH_2_B', 35),
('ARCH_3_A', 35), ('ARCH_3_B', 30),
('ARCH_4_A', 30), ('ARCH_4_B', 25),
('ESTM_1_A', 50), ('ESTM_1_B', 45),
('ESTM_2_A', 45), ('ESTM_2_B', 40),
('ESTM_3_A', 40), ('ESTM_3_B', 35),
('ESTM_4_A', 35), ('ESTM_4_B', 30);
