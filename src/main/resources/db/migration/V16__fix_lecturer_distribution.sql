-- V16: Fix Lecturer Distribution
-- Assign different lecturers to different sections of high-demand courses
-- to avoid lecturer conflicts (one lecturer can't teach 10+ sections)

-- =========================================
-- MTH101 - Currently all assigned to ajohnson
-- Split across 3 lecturers: ajohnson, pokonkwo, aibrahim
-- =========================================

-- COSC groups keep ajohnson
-- SENG groups → pokonkwo
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng')
WHERE code = 'MTH101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name LIKE 'SENG_%'
);

-- ACCT groups → aibrahim  
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng')
WHERE code = 'MTH101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name LIKE 'ACCT_%'
);

-- =========================================
-- GST101 - Currently all assigned to cezeugo
-- Split across lecturers: cezeugo, doluwakayode, cnworgu, badewunmi
-- =========================================

-- COSC groups keep cezeugo
-- SENG groups → doluwakayode
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng')
WHERE code = 'GST101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name LIKE 'SENG_%'
);

-- NRSG groups → cnworgu (already different, but verify)
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng')
WHERE code = 'GST101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name LIKE 'NRSG_%'
);

-- ACCT groups keep doluwakayode (already set in V15)
-- LAWS groups keep cnworgu (already set in V15)
-- MBBS groups keep badewunmi (already set in V15)

-- =========================================
-- COSC201 - Currently all assigned to ebassey
-- Split across lecturers: ebassey (COSC), hyakubu (SENG)
-- =========================================

-- SENG groups → hyakubu
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng')
WHERE code = 'COSC201' AND student_group_id IN (
    SELECT id FROM student_group WHERE name LIKE 'SENG_%'
);

-- =========================================
-- COSC202 - Currently all assigned to hyakubu
-- Give different lecturer for some groups
-- =========================================

-- Groups C, D, E → eodunayo
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng')
WHERE code = 'COSC202' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('COSC_2_C', 'COSC_2_D', 'COSC_2_E')
);

-- =========================================
-- COSC301 - Currently all assigned to cugwu
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → tayodele
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng')
WHERE code = 'COSC301' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('COSC_3_C', 'COSC_3_D', 'COSC_3_E')
);

-- =========================================
-- COSC401 - Currently all assigned to nokafor
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → asoyinka
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng')
WHERE code = 'COSC401' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('COSC_4_C', 'COSC_4_D', 'COSC_4_E')
);

-- =========================================
-- PHY101 - Currently all assigned to aogunleye
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → ieze
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'ieze@babcock.edu.ng')
WHERE code = 'PHY101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('COSC_1_C', 'COSC_1_D', 'COSC_1_E')
);

-- =========================================
-- COSC101 - Currently all assigned to mokoro
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → ataiwo
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng')
WHERE code = 'COSC101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('COSC_1_C', 'COSC_1_D', 'COSC_1_E')
);

-- =========================================
-- COSC102 - Currently all assigned to ataiwo
-- Split across 2 lecturers (since we moved some COSC101 to ataiwo)
-- =========================================

-- Groups C, D, E → cezeagu
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'cezeagu@babcock.edu.ng')
WHERE code = 'COSC102' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('COSC_1_C', 'COSC_1_D', 'COSC_1_E')
);

-- =========================================
-- SENG101 - Currently all assigned to fnwosu
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → iobi
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng')
WHERE code = 'SENG101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('SENG_1_C', 'SENG_1_D', 'SENG_1_E')
);

-- =========================================
-- SENG102 - Currently all assigned to iobi
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → sbabatunde
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng')
WHERE code = 'SENG102' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('SENG_1_C', 'SENG_1_D', 'SENG_1_E')
);

-- =========================================
-- SENG201 - Currently all assigned to sbabatunde
-- Split across 2 lecturers
-- =========================================

-- Groups C, D, E → uamaechi
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'uamaechi@babcock.edu.ng')
WHERE code = 'SENG201' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('SENG_2_C', 'SENG_2_D', 'SENG_2_E')
);

-- =========================================
-- ACCT101 - Change from ajohnson to different lecturers
-- =========================================

-- Use obakare for ACCT101 instead
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'obakare@babcock.edu.ng')
WHERE code = 'ACCT101';

-- Groups C, D → gadeola
UPDATE course SET lecturer_id = (SELECT id FROM lecturer WHERE email = 'gadeola@babcock.edu.ng')
WHERE code = 'ACCT101' AND student_group_id IN (
    SELECT id FROM student_group WHERE name IN ('ACCT_1_C', 'ACCT_1_D')
);
