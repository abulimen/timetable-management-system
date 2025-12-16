-- V10: Babcock University Mock Data for Stress Testing
-- Scale: 64 departments, ~640 courses, ~960 lessons, 140+ lecturers, 100+ rooms

-- =========================================
-- STEP 1: ZONES (Schools/Buildings)
-- =========================================

INSERT INTO zone (name) VALUES
('Management Sciences Building'),
('Nursing Sciences Building'),
('Agricultural Sciences Building'),
('Science And Technology Building'),
('Engineering Complex'),
('Allied Health Sciences Building'),
('Medicine Building Block A'),
('Medicine Building Block B'),
('School of Computing'),
('Environmental Sciences Building'),
('Babcock Business School'),
('Basic and Applied Sciences'),
('Law And Security Studies'),
('Education And Humanities'),
('Social Sciences Building'),
('Lecture Theatre Complex'),
('Central Computer Lab');

-- =========================================
-- STEP 2: FEATURES (INSERT IGNORE to skip existing from V6)
-- =========================================

INSERT IGNORE INTO feature (name) VALUES
('Projector'),
('Whiteboard'),
('AirConditioning'),
('Computers'),
('Laboratory'),
('MedicalEquipment'),
('EngineeringWorkshop'),
('SmartBoard'),
('AudioSystem'),
('ChemicalLab'),
('BiologyLab'),
('PhysicsLab');

-- =========================================
-- STEP 3: ROOMS (7 theatres + 90 classrooms)
-- =========================================

-- 7 Lecture Theatres (200 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('LT-1', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex')),
('LT-2', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex')),
('LT-3', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex')),
('LT-4', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex')),
('LT-5', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex')),
('LT-6', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex')),
('LT-7', 200, (SELECT id FROM zone WHERE name = 'Lecture Theatre Complex'));

-- 10 Management Sciences Classrooms (70 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('MSB-001', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-002', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-003', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-004', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-005', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-006', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-007', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-008', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-009', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building')),
('MSB-010', 70, (SELECT id FROM zone WHERE name = 'Management Sciences Building'));

-- 15 Engineering Classrooms (60 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('ENG-001', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-002', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-003', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-004', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-005', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-006', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-007', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-008', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-009', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-010', 60, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-LAB1', 50, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-LAB2', 50, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-LAB3', 50, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-LAB4', 50, (SELECT id FROM zone WHERE name = 'Engineering Complex')),
('ENG-LAB5', 50, (SELECT id FROM zone WHERE name = 'Engineering Complex'));

-- 20 Computing Classrooms/Labs (50 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('SOC-001', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-002', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-003', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-004', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-005', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-006', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-007', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-008', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-009', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-010', 50, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB1', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB2', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB3', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB4', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB5', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB6', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB7', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB8', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB9', 45, (SELECT id FROM zone WHERE name = 'School of Computing')),
('SOC-LAB10', 45, (SELECT id FROM zone WHERE name = 'School of Computing'));

-- 15 Medicine Classrooms (60 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('MEDA-001', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDA-002', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDA-003', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDA-004', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDA-005', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDA-LAB1', 50, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDA-LAB2', 50, (SELECT id FROM zone WHERE name = 'Medicine Building Block A')),
('MEDB-001', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-002', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-003', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-004', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-005', 60, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-LAB1', 50, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-LAB2', 50, (SELECT id FROM zone WHERE name = 'Medicine Building Block B')),
('MEDB-LAB3', 50, (SELECT id FROM zone WHERE name = 'Medicine Building Block B'));

-- 10 Allied Health Classrooms (55 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('AHS-001', 55, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('AHS-002', 55, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('AHS-003', 55, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('AHS-004', 55, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('AHS-005', 55, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('AHS-LAB1', 45, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('AHS-LAB2', 45, (SELECT id FROM zone WHERE name = 'Allied Health Sciences Building')),
('NSB-001', 55, (SELECT id FROM zone WHERE name = 'Nursing Sciences Building')),
('NSB-002', 55, (SELECT id FROM zone WHERE name = 'Nursing Sciences Building')),
('NSB-003', 55, (SELECT id FROM zone WHERE name = 'Nursing Sciences Building'));

-- 10 Law Classrooms (50 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('LSS-001', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-002', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-003', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-004', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-005', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-006', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-007', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-008', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-009', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies')),
('LSS-010', 50, (SELECT id FROM zone WHERE name = 'Law And Security Studies'));

-- 10 Education/Humanities (55 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('EDH-001', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-002', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-003', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-004', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-005', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-006', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-007', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-008', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-009', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities')),
('EDH-010', 55, (SELECT id FROM zone WHERE name = 'Education And Humanities'));

-- 10 Social Sciences (55 capacity)
INSERT INTO room (name, capacity, zone_id) VALUES
('SSB-001', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-002', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-003', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-004', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-005', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-006', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-007', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-008', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-009', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building')),
('SSB-010', 55, (SELECT id FROM zone WHERE name = 'Social Sciences Building'));

-- Add features to rooms
INSERT INTO room_feature (room_id, feature_id)
SELECT r.id, f.id FROM room r, feature f 
WHERE r.name LIKE 'LT-%' AND f.name IN ('Projector', 'AudioSystem', 'AirConditioning');

INSERT INTO room_feature (room_id, feature_id)
SELECT r.id, f.id FROM room r, feature f 
WHERE r.name LIKE 'SOC-%' AND f.name IN ('Computers', 'Projector', 'AirConditioning');

INSERT INTO room_feature (room_id, feature_id)
SELECT r.id, f.id FROM room r, feature f 
WHERE r.name LIKE 'ENG-LAB%' AND f.name = 'EngineeringWorkshop';

-- =========================================
-- STEP 4: LECTURERS (150)
-- =========================================

INSERT INTO lecturer (name, email) VALUES
('Dr. Adeyemi Johnson', 'ajohnson@babcock.edu.ng'),
('Prof. Okonkwo Patricia', 'pokonkwo@babcock.edu.ng'),
('Dr. Ibrahim Aminu', 'aibrahim@babcock.edu.ng'),
('Dr. Chukwu Emmanuel', 'echukwu@babcock.edu.ng'),
('Prof. Bakare Olufemi', 'obakare@babcock.edu.ng'),
('Dr. Adeola Grace', 'gadeola@babcock.edu.ng'),
('Dr. Nwankwo Stephen', 'snwankwo@babcock.edu.ng'),
('Prof. Adeleke Victoria', 'vadeleke@babcock.edu.ng'),
('Dr. Ogunbiyi Femi', 'fogunbiyi@babcock.edu.ng'),
('Dr. Usman Fatima', 'fusman@babcock.edu.ng'),
('Dr. Akinwale Damilola', 'dakinwale@babcock.edu.ng'),
('Prof. Ogundipe Kayode', 'kogundipe@babcock.edu.ng'),
('Dr. Balogun Yetunde', 'ybalogun@babcock.edu.ng'),
('Dr. Emeka Christian', 'cemeka@babcock.edu.ng'),
('Prof. Oladipo Samuel', 'soladipo@babcock.edu.ng'),
('Dr. Abiodun Temitope', 'tabiodun@babcock.edu.ng'),
('Dr. Olawale Joseph', 'jolawale@babcock.edu.ng'),
('Prof. Nnamdi Blessing', 'bnnamdi@babcock.edu.ng'),
('Dr. Adebayo Funke', 'fadebayo@babcock.edu.ng'),
('Dr. Ojo Oluwaseun', 'oojo@babcock.edu.ng'),
('Prof. Okoro Michael', 'mokoro@babcock.edu.ng'),
('Dr. Taiwo Adebisi', 'ataiwo@babcock.edu.ng'),
('Dr. Ezeagu Chinedu', 'cezeagu@babcock.edu.ng'),
('Prof. Omolara Shade', 'somolara@babcock.edu.ng'),
('Dr. Bassey Effiong', 'ebassey@babcock.edu.ng'),
('Dr. Yakubu Hassan', 'hyakubu@babcock.edu.ng'),
('Prof. Odunayo Elizabeth', 'eodunayo@babcock.edu.ng'),
('Dr. Kolawole Peter', 'pkolawole@babcock.edu.ng'),
('Dr. Ugwu Chisom', 'cugwu@babcock.edu.ng'),
('Prof. Ayodele Tobiloba', 'tayodele@babcock.edu.ng'),
('Dr. Fashola Aderemi', 'afashola@babcock.edu.ng'),
('Dr. Ibe Chinaza', 'cibe@babcock.edu.ng'),
('Prof. Okafor Nneka', 'nokafor@babcock.edu.ng'),
('Dr. Soyinka Abolaji', 'asoyinka@babcock.edu.ng'),
('Dr. Ekwueme Obinna', 'oekwueme@babcock.edu.ng'),
('Prof. Adelakun Yinka', 'yadelakun@babcock.edu.ng'),
('Dr. Nwosu Franklin', 'fnwosu@babcock.edu.ng'),
('Dr. Obi Ifeoma', 'iobi@babcock.edu.ng'),
('Prof. Babatunde Segun', 'sbabatunde@babcock.edu.ng'),
('Dr. Amaechi Ugochi', 'uamaechi@babcock.edu.ng'),
('Dr. Lawal Rasheed', 'rlawal@babcock.edu.ng'),
('Prof. Ezekiel Miriam', 'mezekiel@babcock.edu.ng'),
('Dr. Onuoha Kingsley', 'konuoha@babcock.edu.ng'),
('Dr. Salami Habib', 'hsalami@babcock.edu.ng'),
('Prof. Akindele Omotayo', 'oakindele@babcock.edu.ng'),
('Prof. Ogunleye Adewale', 'aogunleye@babcock.edu.ng'),
('Dr. Eze Ikechukwu', 'ieze@babcock.edu.ng'),
('Dr. Bamidele Oluwakemi', 'obamidele@babcock.edu.ng'),
('Prof. Adekoya Lanre', 'ladekoya@babcock.edu.ng'),
('Dr. Chibuike Emeka', 'echibuike@babcock.edu.ng'),
('Dr. Folarin Adetunji', 'afolarin@babcock.edu.ng'),
('Prof. Ijeoma Nkechi', 'nijeoma@babcock.edu.ng'),
('Dr. Alabi Muyiwa', 'malabi@babcock.edu.ng'),
('Dr. Ozoemena Paul', 'pozoemena@babcock.edu.ng'),
('Prof. Adegoke Simisola', 'sadegoke@babcock.edu.ng'),
('Dr. Nwachukwu Ugonna', 'unwachukwu@babcock.edu.ng'),
('Dr. Efosa Raymond', 'refosa@babcock.edu.ng'),
('Prof. Olumide Gbenga', 'golumide@babcock.edu.ng'),
('Dr. Anyaegbu Judith', 'janyaegbu@babcock.edu.ng'),
('Dr. Odiase Lucky', 'lodiase@babcock.edu.ng'),
('Prof. Adeniyi Folashade', 'fadeniyi@babcock.edu.ng'),
('Dr. Okwudili Emmanuel', 'eokwudili@babcock.edu.ng'),
('Dr. Sowemimo Adejoke', 'asowemimo@babcock.edu.ng'),
('Prof. Chigbo Kenneth', 'kchigbo@babcock.edu.ng'),
('Dr. Ayeni Babajide', 'bayeni@babcock.edu.ng'),
('Prof. Olanrewaju Abiola', 'aolanrewaju@babcock.edu.ng'),
('Dr. Nnaji Sylvester', 'snnaji@babcock.edu.ng'),
('Dr. Adeloye Olumayowa', 'oadeloye@babcock.edu.ng'),
('Prof. Okeke Amaka', 'aokeke@babcock.edu.ng'),
('Dr. Fashanu Dennis', 'dfashanu@babcock.edu.ng'),
('Dr. Olayinka Tolulope', 'tolayinka@babcock.edu.ng'),
('Prof. Nweke Chidinma', 'cnweke@babcock.edu.ng'),
('Dr. Ayanlowo Oluyemi', 'oayanlowo@babcock.edu.ng'),
('Dr. Obiora Valentine', 'vobiora@babcock.edu.ng'),
('Prof. Akande Olumide', 'oakande@babcock.edu.ng'),
('Dr. Uchenna Gracious', 'guchenna@babcock.edu.ng'),
('Dr. Olaniyi Adeyinka', 'aolaniyi@babcock.edu.ng'),
('Prof. Nwokeji Ezinne', 'enwokeji@babcock.edu.ng'),
('Dr. Fadeyi Oluwafunmilayo', 'ofadeyi@babcock.edu.ng'),
('Dr. Okwu Gospel', 'gokwu@babcock.edu.ng'),
('Prof. Adeyemo Olukayode', 'oadeyemo@babcock.edu.ng'),
('Dr. Igwe Chukwuemeka', 'cigwe@babcock.edu.ng'),
('Dr. Afolabi Oluwatosin', 'oafolabi@babcock.edu.ng'),
('Prof. Nnameka Ihuoma', 'innameka@babcock.edu.ng'),
('Dr. Ogbuehi Nnamdi', 'nogbuehi@babcock.edu.ng'),
('Dr. Oyelami Bukola', 'boyelami@babcock.edu.ng'),
('Prof. Chinedum Favour', 'fchinedum@babcock.edu.ng'),
('Dr. Adeniran Moyosore', 'madeniran@babcock.edu.ng'),
('Dr. Okonkwo Kelechi', 'kokonkwo2@babcock.edu.ng'),
('Prof. Ajibola Remi', 'rajibola@babcock.edu.ng'),
('Dr. Ezenwanne Ugochukwu', 'uezenwanne@babcock.edu.ng'),
('Dr. Oyewole Temiloluwa', 'toyewole@babcock.edu.ng'),
('Prof. Nweze Chiamaka', 'cnweze@babcock.edu.ng'),
('Dr. Adegbite Olakunle', 'oadegbite@babcock.edu.ng'),
('Dr. Obioha Sunday', 'sobioha@babcock.edu.ng'),
('Prof. Ogundare Mojisola', 'mogundare@babcock.edu.ng'),
('Dr. Nwaogu Florence', 'fnwaogu@babcock.edu.ng'),
('Dr. Adesanya Adenike', 'aadesanya@babcock.edu.ng'),
('Prof. Obiageli Mercy', 'mobiageli@babcock.edu.ng'),
('Dr. Laolu Folake', 'flaolu@babcock.edu.ng'),
('Dr. Chikwendu Joy', 'jchikwendu@babcock.edu.ng'),
('Prof. Adeogun Omowunmi', 'oadeogun@babcock.edu.ng'),
('Dr. Nwachukwu Esther', 'enwachukwu2@babcock.edu.ng'),
('Dr. Oluwasanmi Rachael', 'roluwasanmi@babcock.edu.ng'),
('Prof. Ekeoma Vivian', 'vekeoma@babcock.edu.ng'),
('Dr. Ademola Comfort', 'cademola@babcock.edu.ng'),
('Dr. Nwando Precious', 'pnwando@babcock.edu.ng'),
('Prof. Oyekan Ronke', 'royekan@babcock.edu.ng'),
('Dr. Ugwuanyi Theresa', 'tugwuanyi@babcock.edu.ng'),
('Dr. Akinola Morenike', 'makinola@babcock.edu.ng'),
('Prof. Okonkwo Ebere', 'eokonkwo@babcock.edu.ng'),
('Dr. Solarin Oluwabukola', 'osolarin@babcock.edu.ng'),
('Dr. Nwakama Chiamaka', 'cnwakama@babcock.edu.ng'),
('Prof. Adekunle Titilope', 'tadekunle@babcock.edu.ng'),
('Dr. Osagie Osazee', 'oosagie@babcock.edu.ng'),
('Prof. Akinyemi Olasunkanmi', 'oakinyemi@babcock.edu.ng'),
('Dr. Okafor Obioma', 'ookafor@babcock.edu.ng'),
('Dr. Fashola Oluwatumininu', 'ofashola2@babcock.edu.ng'),
('Prof. Nwankpa Adaeze', 'anwankpa@babcock.edu.ng'),
('Dr. Olawuyi Adeboye', 'aolawuyi@babcock.edu.ng'),
('Dr. Chukwuma Ngozi', 'nchukwuma@babcock.edu.ng'),
('Prof. Adeyeye Kehinde', 'kadeyeye@babcock.edu.ng'),
('Dr. Nwoye Chidi', 'cnwoye@babcock.edu.ng'),
('Dr. Olowokere Ibukun', 'iolowokere@babcock.edu.ng'),
('Prof. Okoro Chioma', 'cokoro@babcock.edu.ng'),
('Dr. Adebanjo Oluseye', 'oadebanjo@babcock.edu.ng'),
('Dr. Nwachukwu Ikenna', 'inwachukwu@babcock.edu.ng'),
('Prof. Oyinloye Olusola', 'ooyinloye@babcock.edu.ng'),
('Dr. Ekeocha Chijioke', 'cekeocha@babcock.edu.ng'),
('Dr. Adebowale Modupe', 'madebowale@babcock.edu.ng'),
('Prof. Nwachukwu Tochukwu', 'tnwachukwu@babcock.edu.ng'),
('Dr. Oyelade Oluwagbemiga', 'ooyelade@babcock.edu.ng'),
('Dr. Okechukwu Nonso', 'nokechukwu@babcock.edu.ng'),
('Prof. Adenuga Oluwadamilola', 'oadenuga@babcock.edu.ng'),
('Dr. Nwobi Obianuju', 'onwobi@babcock.edu.ng'),
('Prof. Ogbeide Augustine', 'aogbeide@babcock.edu.ng'),
('Dr. Olaniyan Olanrewaju', 'oolaniyan@babcock.edu.ng'),
('Dr. Nwachukwu Amarachi', 'anwachukwu@babcock.edu.ng'),
('Prof. Adesola Oluwatoyosi', 'oadesola@babcock.edu.ng'),
('Dr. Ezeugo Chinwendu', 'cezeugo@babcock.edu.ng'),
('Dr. Oluwakayode Damilare', 'doluwakayode@babcock.edu.ng'),
('Prof. Nworgu Chigozie', 'cnworgu@babcock.edu.ng'),
('Dr. Adewunmi Boluwatife', 'badewunmi@babcock.edu.ng'),
('Dr. Onwuegbuzie Kamsi', 'konwuegbuzie@babcock.edu.ng'),
('Prof. Oyediran Oluwafisayo', 'ooyediran@babcock.edu.ng'),
('Dr. Nwokeukwu Chiemelie', 'cnwokeukwu@babcock.edu.ng'),
('Dr. Adesanya Oluwatobiloba', 'oadesanya2@babcock.edu.ng'),
('Prof. Nweke Onyinyechi', 'onweke@babcock.edu.ng'),
('Dr. Olumuyiwa Mayowa', 'molumuyiwa@babcock.edu.ng'),
('Dr. Nwaogazie Ugochinyere', 'unwaogazie@babcock.edu.ng');
