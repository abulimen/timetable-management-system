-- V15: Realistic Course Data for Babcock University
-- Key principles:
-- 1. All groups (A,B,C,D,E) in each year take the SAME courses
-- 2. Courses belong to "owning" departments but can be taken by other depts (service courses)
-- 3. Each group gets its own course entry to generate separate lessons

-- Clear existing course data (lessons will cascade delete)
DELETE FROM course;

-- =========================================
-- COMPUTER SCIENCE (COSC) DEPARTMENT
-- Year 1: 5 groups (A,B,C,D,E), each takes same courses
-- =========================================

-- Helper: Insert same course for all groups in a year
-- COSC Year 1 Courses (each group A-E gets the same set)

-- GST101 - Use of English I (General Studies, 2hrs/week)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_D')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_E'));

-- MTH101 - General Mathematics I (Math Dept service course, 3hrs/week)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_D')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_E'));

-- COSC101 - Introduction to Computer Science (COSC Dept, 3hrs/week)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC101', 'Introduction to Computer Science', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('COSC101', 'Introduction to Computer Science', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('COSC101', 'Introduction to Computer Science', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('COSC101', 'Introduction to Computer Science', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_D')),
('COSC101', 'Introduction to Computer Science', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_E'));

-- COSC102 - Programming Fundamentals (COSC Dept, 4hrs/week - includes lab)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC102', 'Programming Fundamentals', 4, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('COSC102', 'Programming Fundamentals', 4, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('COSC102', 'Programming Fundamentals', 4, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('COSC102', 'Programming Fundamentals', 4, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_D')),
('COSC102', 'Programming Fundamentals', 4, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_E'));

-- PHY101 - Physics I (Physics Dept service course, 3hrs/week)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('PHY101', 'Physics I', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('PHY101', 'Physics I', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('PHY101', 'Physics I', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('PHY101', 'Physics I', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_D')),
('PHY101', 'Physics I', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_E'));

-- =========================================
-- COSC Year 2 Courses (groups A-E)
-- =========================================

-- COSC201 - Data Structures & Algorithms
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_A')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_B')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_C')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_D')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_E'));

-- COSC202 - Object Oriented Programming
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC202', 'Object Oriented Programming', 4, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_A')),
('COSC202', 'Object Oriented Programming', 4, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_B')),
('COSC202', 'Object Oriented Programming', 4, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_C')),
('COSC202', 'Object Oriented Programming', 4, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_D')),
('COSC202', 'Object Oriented Programming', 4, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_E'));

-- COSC203 - Database Systems
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC203', 'Database Systems', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_A')),
('COSC203', 'Database Systems', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_B')),
('COSC203', 'Database Systems', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_C')),
('COSC203', 'Database Systems', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_D')),
('COSC203', 'Database Systems', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_E'));

-- MTH201 - Discrete Mathematics (Math service course)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MTH201', 'Discrete Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_A')),
('MTH201', 'Discrete Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_B')),
('MTH201', 'Discrete Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_C')),
('MTH201', 'Discrete Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_D')),
('MTH201', 'Discrete Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_E'));

-- =========================================
-- COSC Year 3 Courses (groups A-E)
-- =========================================

-- COSC301 - Operating Systems
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC301', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_A')),
('COSC301', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_B')),
('COSC301', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_C')),
('COSC301', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_D')),
('COSC301', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_E'));

-- COSC302 - Software Engineering
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC302', 'Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_A')),
('COSC302', 'Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_B')),
('COSC302', 'Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_C')),
('COSC302', 'Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_D')),
('COSC302', 'Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_E'));

-- COSC303 - Computer Networks
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC303', 'Computer Networks', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_A')),
('COSC303', 'Computer Networks', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_B')),
('COSC303', 'Computer Networks', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_C')),
('COSC303', 'Computer Networks', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_D')),
('COSC303', 'Computer Networks', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_E'));

-- =========================================
-- COSC Year 4 Courses (groups A-E)
-- =========================================

-- COSC401 - Artificial Intelligence
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC401', 'Artificial Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_A')),
('COSC401', 'Artificial Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_B')),
('COSC401', 'Artificial Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_C')),
('COSC401', 'Artificial Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_D')),
('COSC401', 'Artificial Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_E'));

-- COSC402 - Final Year Project
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC402', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_A')),
('COSC402', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_B')),
('COSC402', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_C')),
('COSC402', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_D')),
('COSC402', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_E'));

-- =========================================
-- SOFTWARE ENGINEERING (SENG) DEPT
-- =========================================

-- SENG Year 1 (all groups take same courses)
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_A')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_B')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_C')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_D')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_E'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_A')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_B')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_C')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_D')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_E'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('SENG101', 'Introduction to Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_A')),
('SENG101', 'Introduction to Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_B')),
('SENG101', 'Introduction to Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_C')),
('SENG101', 'Introduction to Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_D')),
('SENG101', 'Introduction to Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_E'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('SENG102', 'Programming with Python', 4, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_A')),
('SENG102', 'Programming with Python', 4, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_B')),
('SENG102', 'Programming with Python', 4, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_C')),
('SENG102', 'Programming with Python', 4, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_D')),
('SENG102', 'Programming with Python', 4, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_E'));

-- SENG Year 2
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('SENG201', 'Software Design Patterns', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_A')),
('SENG201', 'Software Design Patterns', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_B')),
('SENG201', 'Software Design Patterns', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_C')),
('SENG201', 'Software Design Patterns', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_D')),
('SENG201', 'Software Design Patterns', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_E'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_A')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_B')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_C')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_D')),
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_E'));

-- =========================================
-- NURSING (NRSG) DEPT - Smaller, 3 groups per year
-- =========================================

-- NRSG Year 1
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('NRSG101', 'Fundamentals of Nursing', 4, (SELECT id FROM lecturer WHERE email = 'mogundare@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_A')),
('NRSG101', 'Fundamentals of Nursing', 4, (SELECT id FROM lecturer WHERE email = 'mogundare@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_B')),
('NRSG101', 'Fundamentals of Nursing', 4, (SELECT id FROM lecturer WHERE email = 'mogundare@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_C'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('NRSG102', 'Anatomy & Physiology', 4, (SELECT id FROM lecturer WHERE email = 'fnwaogu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_A')),
('NRSG102', 'Anatomy & Physiology', 4, (SELECT id FROM lecturer WHERE email = 'fnwaogu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_B')),
('NRSG102', 'Anatomy & Physiology', 4, (SELECT id FROM lecturer WHERE email = 'fnwaogu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_C'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_A')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_B')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_C'));

-- NRSG Year 2
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('NRSG201', 'Medical-Surgical Nursing I', 4, (SELECT id FROM lecturer WHERE email = 'aadesanya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_A')),
('NRSG201', 'Medical-Surgical Nursing I', 4, (SELECT id FROM lecturer WHERE email = 'aadesanya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_B')),
('NRSG201', 'Medical-Surgical Nursing I', 4, (SELECT id FROM lecturer WHERE email = 'aadesanya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_C'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('NRSG202', 'Pharmacology for Nurses', 3, (SELECT id FROM lecturer WHERE email = 'mobiageli@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_A')),
('NRSG202', 'Pharmacology for Nurses', 3, (SELECT id FROM lecturer WHERE email = 'mobiageli@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_B')),
('NRSG202', 'Pharmacology for Nurses', 3, (SELECT id FROM lecturer WHERE email = 'mobiageli@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_C'));

-- =========================================
-- ACCOUNTING (ACCT) DEPT
-- =========================================

-- ACCT Year 1
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('ACCT101', 'Principles of Accounting I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_A')),
('ACCT101', 'Principles of Accounting I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_B')),
('ACCT101', 'Principles of Accounting I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_C')),
('ACCT101', 'Principles of Accounting I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_D'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_A')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_B')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_C')),
('MTH101', 'General Mathematics I', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_D'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_A')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_B')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_C')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_D'));

-- ACCT Year 2
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('ACCT201', 'Intermediate Accounting I', 4, (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_A')),
('ACCT201', 'Intermediate Accounting I', 4, (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_B')),
('ACCT201', 'Intermediate Accounting I', 4, (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_C')),
('ACCT201', 'Intermediate Accounting I', 4, (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_D'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('ACCT202', 'Cost Accounting', 3, (SELECT id FROM lecturer WHERE email = 'echukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_A')),
('ACCT202', 'Cost Accounting', 3, (SELECT id FROM lecturer WHERE email = 'echukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_B')),
('ACCT202', 'Cost Accounting', 3, (SELECT id FROM lecturer WHERE email = 'echukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_C')),
('ACCT202', 'Cost Accounting', 3, (SELECT id FROM lecturer WHERE email = 'echukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_D'));

-- =========================================
-- LAW (LAWS) DEPT
-- =========================================

-- LAWS Year 1
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('LAWS101', 'Introduction to Law', 3, (SELECT id FROM lecturer WHERE email = 'oakinyemi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_A')),
('LAWS101', 'Introduction to Law', 3, (SELECT id FROM lecturer WHERE email = 'oakinyemi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_B')),
('LAWS101', 'Introduction to Law', 3, (SELECT id FROM lecturer WHERE email = 'oakinyemi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_C'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('LAWS102', 'Legal Methods', 3, (SELECT id FROM lecturer WHERE email = 'ookafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_A')),
('LAWS102', 'Legal Methods', 3, (SELECT id FROM lecturer WHERE email = 'ookafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_B')),
('LAWS102', 'Legal Methods', 3, (SELECT id FROM lecturer WHERE email = 'ookafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_C'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_A')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_B')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_C'));

-- LAWS Year 2
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('LAWS201', 'Constitutional Law', 4, (SELECT id FROM lecturer WHERE email = 'ofashola2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_A')),
('LAWS201', 'Constitutional Law', 4, (SELECT id FROM lecturer WHERE email = 'ofashola2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_B')),
('LAWS201', 'Constitutional Law', 4, (SELECT id FROM lecturer WHERE email = 'ofashola2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_C'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('LAWS202', 'Law of Contract', 4, (SELECT id FROM lecturer WHERE email = 'anwankpa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_A')),
('LAWS202', 'Law of Contract', 4, (SELECT id FROM lecturer WHERE email = 'anwankpa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_B')),
('LAWS202', 'Law of Contract', 4, (SELECT id FROM lecturer WHERE email = 'anwankpa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_C'));

-- =========================================
-- MEDICINE (MBBS) DEPT - Small groups, intensive courses
-- =========================================

-- MBBS Year 1
INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MBBS101', 'Human Anatomy I', 4, (SELECT id FROM lecturer WHERE email = 'aolanrewaju@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_A')),
('MBBS101', 'Human Anatomy I', 4, (SELECT id FROM lecturer WHERE email = 'aolanrewaju@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_B'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MBBS102', 'Medical Biochemistry I', 4, (SELECT id FROM lecturer WHERE email = 'snnaji@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_A')),
('MBBS102', 'Medical Biochemistry I', 4, (SELECT id FROM lecturer WHERE email = 'snnaji@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_B'));

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'badewunmi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_A')),
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'badewunmi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_B'));
