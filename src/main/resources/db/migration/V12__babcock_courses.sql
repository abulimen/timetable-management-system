-- V12: Babcock Courses (~640 courses: ~10 per department per semester)
-- Each course: ~3 hours/week average, linked to lecturer and student group

-- =========================================
-- COMPUTING COURSES (8 depts × 10 courses = 80)
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- Computer Science Year 1
('COSC101', 'Introduction to Computer Science', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('COSC102', 'Programming Fundamentals I', 4, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('COSC103', 'Discrete Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'cezeagu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('COSC104', 'Digital Logic Design', 3, (SELECT id FROM lecturer WHERE email = 'somolara@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_D')),
-- Computer Science Year 2
('COSC201', 'Data Structures & Algorithms', 4, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_A')),
('COSC202', 'Object Oriented Programming', 4, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_B')),
('COSC203', 'Database Systems I', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_C')),
('COSC204', 'Computer Architecture', 3, (SELECT id FROM lecturer WHERE email = 'pkolawole@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_D')),
-- Computer Science Year 3
('COSC301', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_A')),
('COSC302', 'Software Engineering', 4, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_B')),
('COSC303', 'Computer Networks', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_C')),
('COSC304', 'Web Development', 3, (SELECT id FROM lecturer WHERE email = 'cibe@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_D')),
-- Computer Science Year 4
('COSC401', 'Artificial Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_A')),
('COSC402', 'Machine Learning', 4, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_B')),
('COSC403', 'Cybersecurity', 3, (SELECT id FROM lecturer WHERE email = 'oekwueme@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_C')),
('COSC404', 'Cloud Computing', 3, (SELECT id FROM lecturer WHERE email = 'yadelakun@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_D')),

-- Software Engineering
('SENG101', 'Introduction to Software Engineering', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_A')),
('SENG102', 'Programming with Python', 4, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_B')),
('SENG201', 'Software Design Patterns', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_A')),
('SENG202', 'Agile Methodologies', 3, (SELECT id FROM lecturer WHERE email = 'uamaechi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_B')),
('SENG301', 'Software Testing', 4, (SELECT id FROM lecturer WHERE email = 'rlawal@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_3_A')),
('SENG302', 'DevOps & CI/CD', 3, (SELECT id FROM lecturer WHERE email = 'mezekiel@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_3_B')),
('SENG401', 'Software Architecture', 4, (SELECT id FROM lecturer WHERE email = 'konuoha@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_4_A')),
('SENG402', 'Capstone Project', 4, (SELECT id FROM lecturer WHERE email = 'hsalami@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_4_B')),

-- Information Technology
('ITGY101', 'IT Fundamentals', 3, (SELECT id FROM lecturer WHERE email = 'oakindele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_1_A')),
('ITGY102', 'Office Applications', 2, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_1_B')),
('ITGY201', 'Network Administration', 3, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_2_A')),
('ITGY202', 'System Administration', 3, (SELECT id FROM lecturer WHERE email = 'cezeagu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_2_B')),
('ITGY301', 'IT Security', 4, (SELECT id FROM lecturer WHERE email = 'somolara@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_3_A')),
('ITGY302', 'IT Project Management', 3, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_3_B')),
('ITGY401', 'Enterprise Systems', 3, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_4_A')),
('ITGY402', 'IT Governance', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_4_B'));

-- =========================================
-- ENGINEERING COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- Computer Engineering
('COENG101', 'Engineering Mathematics I', 4, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_1_A')),
('COENG102', 'Introduction to Engineering', 3, (SELECT id FROM lecturer WHERE email = 'ieze@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_1_B')),
('COENG201', 'Circuit Analysis', 4, (SELECT id FROM lecturer WHERE email = 'obamidele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_2_A')),
('COENG202', 'Embedded Systems', 4, (SELECT id FROM lecturer WHERE email = 'ladekoya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_2_B')),
('COENG301', 'Microprocessors', 4, (SELECT id FROM lecturer WHERE email = 'echibuike@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_3_A')),
('COENG302', 'VLSI Design', 3, (SELECT id FROM lecturer WHERE email = 'afolarin@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_3_B')),
('COENG401', 'Computer Engineering Design', 4, (SELECT id FROM lecturer WHERE email = 'nijeoma@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_4_A')),
('COENG402', 'Robotics', 3, (SELECT id FROM lecturer WHERE email = 'malabi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_4_B')),

-- Electrical & Electronic Engineering
('EEENG101', 'Electrical Engineering Fundamentals', 4, (SELECT id FROM lecturer WHERE email = 'pozoemena@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_1_A')),
('EEENG102', 'Engineering Drawing', 2, (SELECT id FROM lecturer WHERE email = 'sadegoke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_1_B')),
('EEENG201', 'Power Systems I', 4, (SELECT id FROM lecturer WHERE email = 'unwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_2_A')),
('EEENG202', 'Electronics I', 4, (SELECT id FROM lecturer WHERE email = 'refosa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_2_B')),
('EEENG301', 'Control Systems', 4, (SELECT id FROM lecturer WHERE email = 'golumide@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_3_A')),
('EEENG302', 'Communication Systems', 3, (SELECT id FROM lecturer WHERE email = 'janyaegbu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_3_B')),
('EEENG401', 'Power Systems II', 4, (SELECT id FROM lecturer WHERE email = 'lodiase@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_4_A')),
('EEENG402', 'Renewable Energy Systems', 3, (SELECT id FROM lecturer WHERE email = 'fadeniyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_4_B')),

-- Mechanical Engineering
('MEENG101', 'Engineering Mechanics', 4, (SELECT id FROM lecturer WHERE email = 'eokwudili@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_1_A')),
('MEENG102', 'Workshop Practice', 3, (SELECT id FROM lecturer WHERE email = 'asowemimo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_1_B')),
('MEENG201', 'Thermodynamics', 4, (SELECT id FROM lecturer WHERE email = 'kchigbo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_2_A')),
('MEENG202', 'Fluid Mechanics', 4, (SELECT id FROM lecturer WHERE email = 'bayeni@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_2_B')),
('MEENG301', 'Machine Design', 4, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_3_A')),
('MEENG302', 'Manufacturing Processes', 3, (SELECT id FROM lecturer WHERE email = 'ieze@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_3_B')),
('MEENG401', 'Heat Transfer', 4, (SELECT id FROM lecturer WHERE email = 'obamidele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_4_A')),
('MEENG402', 'Automotive Engineering', 3, (SELECT id FROM lecturer WHERE email = 'ladekoya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_4_B')),

-- Civil Engineering
('CVENG101', 'Surveying', 3, (SELECT id FROM lecturer WHERE email = 'echibuike@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_1_A')),
('CVENG102', 'Civil Engineering Materials', 3, (SELECT id FROM lecturer WHERE email = 'afolarin@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_1_B')),
('CVENG201', 'Structural Analysis', 4, (SELECT id FROM lecturer WHERE email = 'nijeoma@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_2_A')),
('CVENG202', 'Geotechnical Engineering', 4, (SELECT id FROM lecturer WHERE email = 'malabi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_2_B')),
('CVENG301', 'Reinforced Concrete Design', 4, (SELECT id FROM lecturer WHERE email = 'pozoemena@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_3_A')),
('CVENG302', 'Highway Engineering', 3, (SELECT id FROM lecturer WHERE email = 'sadegoke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_3_B')),
('CVENG401', 'Hydraulic Engineering', 4, (SELECT id FROM lecturer WHERE email = 'unwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_4_A')),
('CVENG402', 'Environmental Engineering', 3, (SELECT id FROM lecturer WHERE email = 'refosa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_4_B'));

-- =========================================
-- MEDICINE COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('MBBS101', 'Human Anatomy I', 4, (SELECT id FROM lecturer WHERE email = 'aolanrewaju@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_A')),
('MBBS102', 'Medical Biochemistry I', 4, (SELECT id FROM lecturer WHERE email = 'snnaji@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_B')),
('MBBS201', 'Human Anatomy II', 4, (SELECT id FROM lecturer WHERE email = 'oadeloye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_2_A')),
('MBBS202', 'Physiology I', 4, (SELECT id FROM lecturer WHERE email = 'aokeke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_2_B')),
('MBBS301', 'Pathology', 4, (SELECT id FROM lecturer WHERE email = 'dfashanu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_3_A')),
('MBBS302', 'Pharmacology', 4, (SELECT id FROM lecturer WHERE email = 'tolayinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_3_B')),
('MBBS401', 'Medicine I', 4, (SELECT id FROM lecturer WHERE email = 'cnweke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_4_A')),
('MBBS402', 'Surgery I', 4, (SELECT id FROM lecturer WHERE email = 'oayanlowo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_4_B')),
('MBBS501', 'Medicine II', 4, (SELECT id FROM lecturer WHERE email = 'vobiora@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_5_A')),
('MBBS502', 'Pediatrics', 4, (SELECT id FROM lecturer WHERE email = 'oakande@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_5_B')),
('MBBS601', 'Obstetrics & Gynecology', 4, (SELECT id FROM lecturer WHERE email = 'guchenna@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_6_A')),
('MBBS602', 'Community Medicine', 4, (SELECT id FROM lecturer WHERE email = 'aolaniyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_6_B'));

-- =========================================
-- NURSING COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('NRSG101', 'Fundamentals of Nursing', 4, (SELECT id FROM lecturer WHERE email = 'mogundare@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_A')),
('NRSG102', 'Anatomy & Physiology', 4, (SELECT id FROM lecturer WHERE email = 'fnwaogu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_B')),
('NRSG201', 'Medical-Surgical Nursing I', 4, (SELECT id FROM lecturer WHERE email = 'aadesanya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_A')),
('NRSG202', 'Pharmacology for Nurses', 3, (SELECT id FROM lecturer WHERE email = 'mobiageli@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_B')),
('NRSG301', 'Maternal & Child Health', 4, (SELECT id FROM lecturer WHERE email = 'flaolu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_3_A')),
('NRSG302', 'Mental Health Nursing', 3, (SELECT id FROM lecturer WHERE email = 'jchikwendu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_3_B')),
('NRSG401', 'Community Health Nursing', 4, (SELECT id FROM lecturer WHERE email = 'oadeogun@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_4_A')),
('NRSG402', 'Nursing Leadership', 3, (SELECT id FROM lecturer WHERE email = 'enwachukwu2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_4_B'));

-- =========================================
-- MANAGEMENT COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('ACCT101', 'Principles of Accounting I', 3, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_A')),
('ACCT102', 'Business Mathematics', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_B')),
('ACCT201', 'Intermediate Accounting I', 4, (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_A')),
('ACCT202', 'Cost Accounting', 3, (SELECT id FROM lecturer WHERE email = 'echukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_2_B')),
('ACCT301', 'Auditing', 4, (SELECT id FROM lecturer WHERE email = 'obakare@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_3_A')),
('ACCT302', 'Taxation', 3, (SELECT id FROM lecturer WHERE email = 'gadeola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_3_B')),
('ACCT401', 'Public Sector Accounting', 3, (SELECT id FROM lecturer WHERE email = 'snwankwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_4_A')),
('ACCT402', 'Forensic Accounting', 3, (SELECT id FROM lecturer WHERE email = 'vadeleke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_4_B')),

-- Business Administration
('BAM101', 'Introduction to Business', 3, (SELECT id FROM lecturer WHERE email = 'fogunbiyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_1_A')),
('BAM102', 'Principles of Management', 3, (SELECT id FROM lecturer WHERE email = 'fusman@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_1_B')),
('BAM201', 'Organizational Behavior', 3, (SELECT id FROM lecturer WHERE email = 'dakinwale@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_2_A')),
('BAM202', 'Marketing Management', 3, (SELECT id FROM lecturer WHERE email = 'kogundipe@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_2_B')),
('BAM301', 'Operations Management', 4, (SELECT id FROM lecturer WHERE email = 'ybalogun@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_3_A')),
('BAM302', 'Strategic Management', 3, (SELECT id FROM lecturer WHERE email = 'cemeka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_3_B')),
('BAM401', 'Entrepreneurship', 3, (SELECT id FROM lecturer WHERE email = 'soladipo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_4_A')),
('BAM402', 'Business Policy', 3, (SELECT id FROM lecturer WHERE email = 'tabiodun@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_4_B'));

-- =========================================
-- LAW COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('LAWS101', 'Introduction to Law', 3, (SELECT id FROM lecturer WHERE email = 'oakinyemi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_A')),
('LAWS102', 'Legal Methods', 3, (SELECT id FROM lecturer WHERE email = 'ookafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_1_B')),
('LAWS201', 'Constitutional Law', 4, (SELECT id FROM lecturer WHERE email = 'ofashola2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_A')),
('LAWS202', 'Law of Contract', 4, (SELECT id FROM lecturer WHERE email = 'anwankpa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_B')),
('LAWS301', 'Law of Torts', 4, (SELECT id FROM lecturer WHERE email = 'aolawuyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_3_A')),
('LAWS302', 'Criminal Law', 4, (SELECT id FROM lecturer WHERE email = 'nchukwuma@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_3_B')),
('LAWS401', 'Company Law', 4, (SELECT id FROM lecturer WHERE email = 'kadeyeye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_4_A')),
('LAWS402', 'International Law', 3, (SELECT id FROM lecturer WHERE email = 'cnwoye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_4_B'));

-- =========================================
-- SOCIAL SCIENCES COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('ECON101', 'Principles of Economics', 3, (SELECT id FROM lecturer WHERE email = 'iolowokere@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_1_A')),
('ECON102', 'Mathematics for Economics', 3, (SELECT id FROM lecturer WHERE email = 'cokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_1_B')),
('ECON201', 'Microeconomics', 4, (SELECT id FROM lecturer WHERE email = 'oadebanjo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_2_A')),
('ECON202', 'Macroeconomics', 4, (SELECT id FROM lecturer WHERE email = 'inwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_2_B')),
('ECON301', 'Development Economics', 3, (SELECT id FROM lecturer WHERE email = 'ooyinloye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_3_A')),
('ECON302', 'Monetary Economics', 3, (SELECT id FROM lecturer WHERE email = 'cekeocha@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_3_B')),
('ECON401', 'Econometrics', 4, (SELECT id FROM lecturer WHERE email = 'madebowale@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_4_A')),
('ECON402', 'Public Finance', 3, (SELECT id FROM lecturer WHERE email = 'tnwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_4_B')),

-- Mass Communication
('MCOM101', 'Introduction to Mass Communication', 3, (SELECT id FROM lecturer WHERE email = 'ooyelade@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_1_A')),
('MCOM102', 'Communication Theory', 3, (SELECT id FROM lecturer WHERE email = 'nokechukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_1_B')),
('MCOM201', 'News Writing & Reporting', 4, (SELECT id FROM lecturer WHERE email = 'oadenuga@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_2_A')),
('MCOM202', 'Broadcast Journalism', 3, (SELECT id FROM lecturer WHERE email = 'onwobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_2_B')),
('MCOM301', 'Public Relations', 3, (SELECT id FROM lecturer WHERE email = 'aogbeide@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_3_A')),
('MCOM302', 'Advertising', 3, (SELECT id FROM lecturer WHERE email = 'oolaniyan@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_3_B')),
('MCOM401', 'Media Law & Ethics', 3, (SELECT id FROM lecturer WHERE email = 'anwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_4_A')),
('MCOM402', 'Digital Media', 3, (SELECT id FROM lecturer WHERE email = 'oadesola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MCOM_4_B'));

-- =========================================
-- GENERAL STUDIES (Cross-departmental)
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST101', 'Use of English I', 2, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('GST102', 'Nigerian History', 2, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_A')),
('GST103', 'Logic & Critical Thinking', 2, (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_1_A')),
('GST104', 'Citizenship Education', 2, (SELECT id FROM lecturer WHERE email = 'badewunmi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAM_1_A')),
('GST201', 'Peace & Conflict Resolution', 2, (SELECT id FROM lecturer WHERE email = 'konwuegbuzie@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LAWS_2_A')),
('GST202', 'Entrepreneurship Studies', 2, (SELECT id FROM lecturer WHERE email = 'ooyediran@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ECON_2_A'));
