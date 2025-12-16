-- V13: Additional Babcock Courses to reach ~500+ courses
-- More courses for computing departments (multiple groups per course)

-- =========================================
-- ADDITIONAL COMPUTING COURSES (courses for other groups)
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- COSC courses for remaining groups
('COSC105', 'Computer Applications', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_E')),
('COSC106', 'Introduction to Programming Lab', 3, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_A')),
('COSC107', 'Mathematics for Computing', 3, (SELECT id FROM lecturer WHERE email = 'cezeagu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_B')),
('COSC108', 'Technical Writing', 2, (SELECT id FROM lecturer WHERE email = 'somolara@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_1_C')),
('COSC205', 'Database Systems II', 3, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_E')),
('COSC206', 'System Analysis & Design', 3, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_A')),
('COSC207', 'Numerical Methods', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_B')),
('COSC208', 'Statistics for CS', 3, (SELECT id FROM lecturer WHERE email = 'pkolawole@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_2_C')),
('COSC305', 'Compiler Design', 3, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_E')),
('COSC306', 'Mobile App Development', 4, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_A')),
('COSC307', 'Graphics & Multimedia', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_B')),
('COSC308', 'Human Computer Interaction', 3, (SELECT id FROM lecturer WHERE email = 'cibe@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_3_C')),
('COSC405', 'Data Mining', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_E')),
('COSC406', 'Computer Vision', 3, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_A')),
('COSC407', 'Natural Language Processing', 3, (SELECT id FROM lecturer WHERE email = 'oekwueme@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_B')),
('COSC408', 'Research Project', 4, (SELECT id FROM lecturer WHERE email = 'yadelakun@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COSC_4_C')),

-- SENG additional courses
('SENG103', 'Introduction to Web Tech', 3, (SELECT id FROM lecturer WHERE email = 'fnwosu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_C')),
('SENG104', 'Software Tools', 3, (SELECT id FROM lecturer WHERE email = 'iobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_D')),
('SENG105', 'Linux Fundamentals', 3, (SELECT id FROM lecturer WHERE email = 'sbabatunde@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_1_E')),
('SENG203', 'Requirements Engineering', 3, (SELECT id FROM lecturer WHERE email = 'uamaechi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_C')),
('SENG204', 'Software Metrics', 3, (SELECT id FROM lecturer WHERE email = 'rlawal@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_D')),
('SENG205', 'Software Quality Assurance', 3, (SELECT id FROM lecturer WHERE email = 'mezekiel@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_2_E')),
('SENG303', 'Software Configuration Mgmt', 3, (SELECT id FROM lecturer WHERE email = 'konuoha@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_3_C')),
('SENG304', 'Microservices Architecture', 4, (SELECT id FROM lecturer WHERE email = 'hsalami@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_3_D')),
('SENG305', 'Cloud Native Development', 4, (SELECT id FROM lecturer WHERE email = 'oakindele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_3_E')),
('SENG403', 'Software Project Management', 3, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_4_C')),
('SENG404', 'Software Maintenance', 3, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_4_D')),
('SENG405', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'cezeagu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'SENG_4_E')),

-- ITGY additional courses
('ITGY103', 'Hardware Fundamentals', 3, (SELECT id FROM lecturer WHERE email = 'somolara@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_1_C')),
('ITGY104', 'Operating Systems Basics', 3, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_1_D')),
('ITGY105', 'Intro to Networking', 3, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_1_E')),
('ITGY203', 'Virtualization Tech', 3, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_2_C')),
('ITGY204', 'Cloud Services', 3, (SELECT id FROM lecturer WHERE email = 'pkolawole@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_2_D')),
('ITGY205', 'Help Desk Management', 3, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_2_E')),
('ITGY303', 'IT Service Management', 3, (SELECT id FROM lecturer WHERE email = 'tayodele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_3_C')),
('ITGY304', 'Disaster Recovery', 3, (SELECT id FROM lecturer WHERE email = 'afashola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_3_D')),
('ITGY305', 'IT Audit', 3, (SELECT id FROM lecturer WHERE email = 'cibe@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_3_E')),
('ITGY403', 'IT Strategy', 4, (SELECT id FROM lecturer WHERE email = 'nokafor@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_4_C')),
('ITGY404', 'Business Intelligence', 4, (SELECT id FROM lecturer WHERE email = 'asoyinka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_4_D')),
('ITGY405', 'Industrial Attachment', 6, (SELECT id FROM lecturer WHERE email = 'oekwueme@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ITGY_4_E')),

-- COENG additional courses
('COENG103', 'Engineering Physics', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_1_C')),
('COENG104', 'Engineering Chemistry', 3, (SELECT id FROM lecturer WHERE email = 'ieze@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_1_D')),
('COENG105', 'Technical Drawing', 3, (SELECT id FROM lecturer WHERE email = 'obamidele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_1_E')),
('COENG203', 'Signals & Systems', 4, (SELECT id FROM lecturer WHERE email = 'ladekoya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_2_C')),
('COENG204', 'Digital Signal Processing', 4, (SELECT id FROM lecturer WHERE email = 'echibuike@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_2_D')),
('COENG205', 'Analog Electronics', 3, (SELECT id FROM lecturer WHERE email = 'afolarin@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_2_E')),
('COENG303', 'Computer Networks', 4, (SELECT id FROM lecturer WHERE email = 'nijeoma@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_3_C')),
('COENG304', 'Operating Systems', 4, (SELECT id FROM lecturer WHERE email = 'malabi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_3_D')),
('COENG305', 'Real-Time Systems', 3, (SELECT id FROM lecturer WHERE email = 'pozoemena@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_3_E')),
('COENG403', 'FPGA Design', 4, (SELECT id FROM lecturer WHERE email = 'sadegoke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_4_C')),
('COENG404', 'IoT Systems', 3, (SELECT id FROM lecturer WHERE email = 'unwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_4_D')),
('COENG405', 'Final Year Project', 6, (SELECT id FROM lecturer WHERE email = 'refosa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'COENG_4_E'));

-- =========================================
-- ADDITIONAL ENGINEERING COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- EEENG additional
('EEENG103', 'Engineering Physics', 3, (SELECT id FROM lecturer WHERE email = 'golumide@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_1_C')),
('EEENG203', 'Circuit Theory II', 4, (SELECT id FROM lecturer WHERE email = 'janyaegbu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_2_C')),
('EEENG303', 'Digital Electronics', 4, (SELECT id FROM lecturer WHERE email = 'lodiase@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_3_C')),
('EEENG403', 'Power Electronics', 4, (SELECT id FROM lecturer WHERE email = 'fadeniyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_4_C')),

-- MEENG additional
('MEENG103', 'Engineering Drawing II', 3, (SELECT id FROM lecturer WHERE email = 'eokwudili@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_1_C')),
('MEENG203', 'Strength of Materials', 4, (SELECT id FROM lecturer WHERE email = 'asowemimo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_2_C')),
('MEENG303', 'Dynamics', 4, (SELECT id FROM lecturer WHERE email = 'kchigbo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_3_C')),
('MEENG403', 'Mechatronics', 4, (SELECT id FROM lecturer WHERE email = 'bayeni@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_4_C')),

-- CVENG additional
('CVENG103', 'Building Construction', 3, (SELECT id FROM lecturer WHERE email = 'aogunleye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_1_C')),
('CVENG203', 'Soil Mechanics', 4, (SELECT id FROM lecturer WHERE email = 'ieze@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_2_C')),
('CVENG303', 'Steel Structures', 4, (SELECT id FROM lecturer WHERE email = 'obamidele@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_3_C')),
('CVENG403', 'Construction Management', 4, (SELECT id FROM lecturer WHERE email = 'ladekoya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_4_C'));

-- =========================================
-- ADDITIONAL MEDICINE & HEALTH COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- More MBBS courses
('MBBS103', 'Medical Physics', 3, (SELECT id FROM lecturer WHERE email = 'enwokeji@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_A')),
('MBBS104', 'Medical Chemistry', 3, (SELECT id FROM lecturer WHERE email = 'ofadeyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_B')),
('MBBS203', 'Histology', 4, (SELECT id FROM lecturer WHERE email = 'gokwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_2_A')),
('MBBS204', 'Embryology', 3, (SELECT id FROM lecturer WHERE email = 'oadeyemo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_2_B')),
('MBBS303', 'Microbiology', 4, (SELECT id FROM lecturer WHERE email = 'cigwe@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_3_A')),
('MBBS304', 'Parasitology', 3, (SELECT id FROM lecturer WHERE email = 'oafolabi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_3_B')),
('MBBS403', 'Psychiatry', 4, (SELECT id FROM lecturer WHERE email = 'innameka@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_4_A')),
('MBBS404', 'Anesthesiology', 3, (SELECT id FROM lecturer WHERE email = 'nogbuehi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_4_B')),
('MBBS503', 'Radiology', 4, (SELECT id FROM lecturer WHERE email = 'boyelami@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_5_A')),
('MBBS504', 'Clinical Skills', 4, (SELECT id FROM lecturer WHERE email = 'fchinedum@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_5_B')),
('MBBS603', 'Medical Ethics', 3, (SELECT id FROM lecturer WHERE email = 'madeniran@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_6_A')),
('MBBS604', 'Emergency Medicine', 4, (SELECT id FROM lecturer WHERE email = 'kokonkwo2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_6_B')),

-- More NRSG courses
('NRSG103', 'Medical Terminology', 2, (SELECT id FROM lecturer WHERE email = 'roluwasanmi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_C')),
('NRSG203', 'Health Assessment', 4, (SELECT id FROM lecturer WHERE email = 'vekeoma@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_2_C')),
('NRSG303', 'Pediatric Nursing', 4, (SELECT id FROM lecturer WHERE email = 'cademola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_3_C')),
('NRSG403', 'Critical Care Nursing', 4, (SELECT id FROM lecturer WHERE email = 'pnwando@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_4_C')),

-- MLSC courses
('MLSC101', 'Basic Medical Sciences', 4, (SELECT id FROM lecturer WHERE email = 'royekan@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_1_A')),
('MLSC102', 'Medical Lab Intro', 3, (SELECT id FROM lecturer WHERE email = 'tugwuanyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_1_B')),
('MLSC103', 'Lab Safety', 2, (SELECT id FROM lecturer WHERE email = 'makinola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_1_C')),
('MLSC201', 'Hematology I', 4, (SELECT id FROM lecturer WHERE email = 'eokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_2_A')),
('MLSC202', 'Clinical Chemistry I', 4, (SELECT id FROM lecturer WHERE email = 'osolarin@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_2_B')),
('MLSC203', 'Medical Microbiology', 4, (SELECT id FROM lecturer WHERE email = 'cnwakama@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_2_C')),
('MLSC301', 'Hematology II', 4, (SELECT id FROM lecturer WHERE email = 'tadekunle@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_3_A')),
('MLSC302', 'Parasitology', 4, (SELECT id FROM lecturer WHERE email = 'oosagie@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_3_B')),
('MLSC303', 'Immunology', 4, (SELECT id FROM lecturer WHERE email = 'mogundare@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_3_C')),
('MLSC401', 'Blood Banking', 4, (SELECT id FROM lecturer WHERE email = 'fnwaogu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_4_A')),
('MLSC402', 'Histopathology', 4, (SELECT id FROM lecturer WHERE email = 'aadesanya@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_4_B')),
('MLSC403', 'Clinical Rotation', 6, (SELECT id FROM lecturer WHERE email = 'mobiageli@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MLSC_4_C'));

-- =========================================
-- ADDITIONAL MANAGEMENT & LAW COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- Finance courses
('BAFN101', 'Financial Accounting', 3, (SELECT id FROM lecturer WHERE email = 'jolawale@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_1_A')),
('BAFN102', 'Business Economics', 3, (SELECT id FROM lecturer WHERE email = 'bnnamdi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_1_B')),
('BAFN201', 'Corporate Finance', 4, (SELECT id FROM lecturer WHERE email = 'fadebayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_2_A')),
('BAFN202', 'Financial Markets', 3, (SELECT id FROM lecturer WHERE email = 'oojo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_2_B')),
('BAFN301', 'Investment Analysis', 4, (SELECT id FROM lecturer WHERE email = 'ajohnson@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_3_A')),
('BAFN302', 'Portfolio Management', 3, (SELECT id FROM lecturer WHERE email = 'pokonkwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_3_B')),
('BAFN401', 'International Finance', 4, (SELECT id FROM lecturer WHERE email = 'aibrahim@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_4_A')),
('BAFN402', 'Financial Modeling', 4, (SELECT id FROM lecturer WHERE email = 'echukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BAFN_4_B')),

-- Additional Law courses
('BCOL101', 'Commercial Law Intro', 3, (SELECT id FROM lecturer WHERE email = 'iolowokere@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_1_A')),
('BCOL102', 'Business Law', 3, (SELECT id FROM lecturer WHERE email = 'cokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_1_B')),
('BCOL201', 'Trade Law', 4, (SELECT id FROM lecturer WHERE email = 'oadebanjo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_2_A')),
('BCOL202', 'Banking Law', 4, (SELECT id FROM lecturer WHERE email = 'inwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_2_B')),
('BCOL301', 'Insurance Law', 4, (SELECT id FROM lecturer WHERE email = 'ooyinloye@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_3_A')),
('BCOL302', 'Securities Law', 3, (SELECT id FROM lecturer WHERE email = 'cekeocha@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_3_B')),
('BCOL401', 'Competition Law', 4, (SELECT id FROM lecturer WHERE email = 'madebowale@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_4_A')),
('BCOL402', 'Intellectual Property', 3, (SELECT id FROM lecturer WHERE email = 'tnwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCOL_4_B'));

-- =========================================
-- ADDITIONAL SOCIAL SCIENCES & EDUCATION
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- Political Science
('PSPA101', 'Intro to Political Science', 3, (SELECT id FROM lecturer WHERE email = 'ooyelade@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_1_A')),
('PSPA102', 'Nigerian Government', 3, (SELECT id FROM lecturer WHERE email = 'nokechukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_1_B')),
('PSPA201', 'Comparative Politics', 4, (SELECT id FROM lecturer WHERE email = 'oadenuga@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_2_A')),
('PSPA202', 'Political Theory', 3, (SELECT id FROM lecturer WHERE email = 'onwobi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_2_B')),
('PSPA301', 'Public Administration', 4, (SELECT id FROM lecturer WHERE email = 'aogbeide@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_3_A')),
('PSPA302', 'Local Government', 3, (SELECT id FROM lecturer WHERE email = 'oolaniyan@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_3_B')),
('PSPA401', 'International Relations', 4, (SELECT id FROM lecturer WHERE email = 'anwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_4_A')),
('PSPA402', 'Research Methods', 4, (SELECT id FROM lecturer WHERE email = 'oadesola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'PSPA_4_B')),

-- Education courses
('EDFO101', 'Foundations of Education', 3, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_1_A')),
('EDFO102', 'Philosophy of Education', 3, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_1_B')),
('EDFO201', 'Educational Psychology', 4, (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_2_A')),
('EDFO202', 'Sociology of Education', 3, (SELECT id FROM lecturer WHERE email = 'badewunmi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_2_B')),
('EDFO301', 'Curriculum Development', 4, (SELECT id FROM lecturer WHERE email = 'konwuegbuzie@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_3_A')),
('EDFO302', 'Educational Technology', 3, (SELECT id FROM lecturer WHERE email = 'ooyediran@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_3_B')),
('EDFO401', 'Teaching Practice', 6, (SELECT id FROM lecturer WHERE email = 'cnwokeukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_4_A')),
('EDFO402', 'Educational Management', 3, (SELECT id FROM lecturer WHERE email = 'oadesanya2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EDFO_4_B'));

-- =========================================
-- ADDITIONAL SCIENCE COURSES
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- Microbiology
('MBGY101', 'General Biology', 4, (SELECT id FROM lecturer WHERE email = 'onweke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_1_A')),
('MBGY102', 'General Chemistry', 4, (SELECT id FROM lecturer WHERE email = 'molumuyiwa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_1_B')),
('MBGY201', 'Microbiology I', 4, (SELECT id FROM lecturer WHERE email = 'unwaogazie@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_2_A')),
('MBGY202', 'Microbial Physiology', 4, (SELECT id FROM lecturer WHERE email = 'onweke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_2_B')),
('MBGY301', 'Industrial Microbiology', 4, (SELECT id FROM lecturer WHERE email = 'molumuyiwa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_3_A')),
('MBGY302', 'Environmental Microbiology', 3, (SELECT id FROM lecturer WHERE email = 'unwaogazie@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_3_B')),
('MBGY401', 'Food Microbiology', 4, (SELECT id FROM lecturer WHERE email = 'onweke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_4_A')),
('MBGY402', 'Microbiology Research', 6, (SELECT id FROM lecturer WHERE email = 'molumuyiwa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBGY_4_B')),

-- Biochemistry
('BCHM101', 'General Chemistry', 4, (SELECT id FROM lecturer WHERE email = 'rajibola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_1_A')),
('BCHM102', 'Intro to Biochemistry', 4, (SELECT id FROM lecturer WHERE email = 'uezenwanne@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_1_B')),
('BCHM201', 'Organic Chemistry', 4, (SELECT id FROM lecturer WHERE email = 'toyewole@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_2_A')),
('BCHM202', 'Carbohydrate Metabolism', 4, (SELECT id FROM lecturer WHERE email = 'cnweze@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_2_B')),
('BCHM301', 'Protein Chemistry', 4, (SELECT id FROM lecturer WHERE email = 'oadegbite@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_3_A')),
('BCHM302', 'Enzymology', 4, (SELECT id FROM lecturer WHERE email = 'sobioha@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_3_B')),
('BCHM401', 'Molecular Biology', 4, (SELECT id FROM lecturer WHERE email = 'rajibola@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_4_A')),
('BCHM402', 'Clinical Biochemistry', 4, (SELECT id FROM lecturer WHERE email = 'uezenwanne@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'BCHM_4_B'));

-- =========================================
-- ADDITIONAL HUMANITIES & ARCHITECTURE
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
-- Languages
('LALI101', 'French I', 3, (SELECT id FROM lecturer WHERE email = 'cezeugo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_1_A')),
('LALI102', 'English Literature', 3, (SELECT id FROM lecturer WHERE email = 'doluwakayode@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_1_B')),
('LALI201', 'Creative Writing', 3, (SELECT id FROM lecturer WHERE email = 'cnworgu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_2_A')),
('LALI202', 'African Literature', 3, (SELECT id FROM lecturer WHERE email = 'badewunmi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_2_B')),
('LALI301', 'Linguistics', 4, (SELECT id FROM lecturer WHERE email = 'konwuegbuzie@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_3_A')),
('LALI302', 'Phonetics', 3, (SELECT id FROM lecturer WHERE email = 'ooyediran@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_3_B')),
('LALI401', 'Translation Studies', 4, (SELECT id FROM lecturer WHERE email = 'cnwokeukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_4_A')),
('LALI402', 'Literary Criticism', 3, (SELECT id FROM lecturer WHERE email = 'oadesanya2@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'LALI_4_B')),

-- Architecture & Estate
('ARCH101', 'Intro to Architecture', 4, (SELECT id FROM lecturer WHERE email = 'echibuike@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_1_A')),
('ARCH102', 'Architectural Drawing', 4, (SELECT id FROM lecturer WHERE email = 'afolarin@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_1_B')),
('ARCH201', 'Design Studio I', 6, (SELECT id FROM lecturer WHERE email = 'nijeoma@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_2_A')),
('ARCH202', 'Building Construction', 4, (SELECT id FROM lecturer WHERE email = 'malabi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_2_B')),
('ARCH301', 'Design Studio II', 6, (SELECT id FROM lecturer WHERE email = 'pozoemena@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_3_A')),
('ARCH302', 'Environmental Design', 4, (SELECT id FROM lecturer WHERE email = 'sadegoke@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_3_B')),
('ARCH401', 'Advanced Design', 6, (SELECT id FROM lecturer WHERE email = 'unwachukwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_4_A')),
('ARCH402', 'Urban Planning', 4, (SELECT id FROM lecturer WHERE email = 'refosa@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ARCH_4_B')),

-- Estate Management
('ESTM101', 'Intro to Estate Mgmt', 3, (SELECT id FROM lecturer WHERE email = 'golumide@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_1_A')),
('ESTM102', 'Land Economics', 3, (SELECT id FROM lecturer WHERE email = 'janyaegbu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_1_B')),
('ESTM201', 'Property Valuation I', 4, (SELECT id FROM lecturer WHERE email = 'lodiase@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_2_A')),
('ESTM202', 'Building Services', 3, (SELECT id FROM lecturer WHERE email = 'fadeniyi@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_2_B')),
('ESTM301', 'Property Valuation II', 4, (SELECT id FROM lecturer WHERE email = 'eokwudili@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_3_A')),
('ESTM302', 'Property Law', 4, (SELECT id FROM lecturer WHERE email = 'asowemimo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_3_B')),
('ESTM401', 'Property Development', 4, (SELECT id FROM lecturer WHERE email = 'kchigbo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_4_A')),
('ESTM402', 'Facility Management', 3, (SELECT id FROM lecturer WHERE email = 'bayeni@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ESTM_4_B'));

-- =========================================
-- GENERAL STUDIES FOR DIFFERENT DEPARTMENTS
-- =========================================

INSERT INTO course (code, name, total_weekly_hours, lecturer_id, student_group_id) VALUES
('GST105', 'Intro to Computers', 2, (SELECT id FROM lecturer WHERE email = 'mokoro@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_A')),
('GST106', 'Intro to Computers', 2, (SELECT id FROM lecturer WHERE email = 'ataiwo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'ACCT_1_B')),
('GST107', 'Use of English', 2, (SELECT id FROM lecturer WHERE email = 'cezeagu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_A')),
('GST108', 'Use of English', 2, (SELECT id FROM lecturer WHERE email = 'somolara@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MBBS_1_B')),
('GST109', 'Scholar Skills', 2, (SELECT id FROM lecturer WHERE email = 'ebassey@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_A')),
('GST110', 'Scholar Skills', 2, (SELECT id FROM lecturer WHERE email = 'hyakubu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'NRSG_1_B')),
('GST203', 'Health Education', 2, (SELECT id FROM lecturer WHERE email = 'eodunayo@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'EEENG_2_A')),
('GST204', 'Health Education', 2, (SELECT id FROM lecturer WHERE email = 'pkolawole@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'MEENG_2_A')),
('GST205', 'Civic Responsibility', 2, (SELECT id FROM lecturer WHERE email = 'cugwu@babcock.edu.ng'), (SELECT id FROM student_group WHERE name = 'CVENG_2_A'));
