import csv
import random
from faker import Faker

fake = Faker()

def read_csv(file_path):
    with open(file_path, 'r', newline='') as csvfile:
        reader = csv.reader(csvfile)
        header = next(reader)
        data = [row for row in reader]
    return header, data

def write_csv(file_path, header, data):
    with open(file_path, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(header)
        writer.writerows(data)

# Read existing data
zones_header, zones_data = read_csv('csv_files/zones.csv')
features_header, features_data = read_csv('csv_files/features.csv')
lecturers_header, lecturers_data = read_csv('csv_files/lecturers.csv')
student_groups_header, student_groups_data = read_csv('csv_files/student_groups.csv')
rooms_header, rooms_data = read_csv('csv_files/rooms.csv')
courses_header, courses_data = read_csv('csv_files/courses.csv')

print("Finished reading existing data.")

# --- Generate Lecturers ---
print("Generating new lecturers...")
existing_lecturer_emails = {row[1] for row in lecturers_data if len(row) > 1 and row[1]}
new_lecturers = []
for _ in range(1000):
    name = fake.name()
    email = f"{name.lower().replace(' ', '.').replace('-', '')}{random.randint(1,99)}@university.edu"
    while email in existing_lecturer_emails:
        email = f"{name.lower().replace(' ', '.').replace('-', '')}{random.randint(100,999)}@university.edu"

    new_lecturers.append([name, email, ''])
    existing_lecturer_emails.add(email)

lecturers_data.extend(new_lecturers)

# --- Generate Student Groups ---
print("Generating new student groups...")
existing_group_names = {row[0] for row in student_groups_data}
new_student_groups = []
new_departments = [
    "Aerospace Studies", "Anthropology", "Art History", "Astronomy", "Biochemistry",
    "Classical Studies", "Cognitive Science", "Communication Studies", "Criminology",
    "Environmental Science", "Film Studies", "Gender Studies", "Geology", "Linguistics",
    "Neuroscience", "Oceanography", "Public Policy", "Religious Studies", "Social Work", "Urban Planning"
]

for dept in new_departments:
    for year in range(1, 5):
        parent_group_name = f"{dept} Year{year}"
        if parent_group_name not in existing_group_names:
            new_student_groups.append([parent_group_name, '', ''])
            existing_group_names.add(parent_group_name)

            for i in range(1, 4):
                subgroup_name = f"{dept} {year}{chr(ord('A') + i - 1)}"
                size = random.randint(20, 50)
                new_student_groups.append([subgroup_name, size, parent_group_name])
                existing_group_names.add(subgroup_name)

student_groups_data.extend(new_student_groups)

# --- Generate Rooms ---
print("Generating new rooms...")
existing_room_names = {row[0] for row in rooms_data}
new_rooms = []
zone_names = [row[0] for row in zones_data]
feature_names = [row[0] for row in features_data]

for i in range(500):
    room_name = f"GenRoom-{i+1}"
    if room_name not in existing_room_names:
        capacity = random.choice([25, 30, 40, 50, 60, 80, 100, 120, 150, 200, 250, 300])
        zone = random.choice(zone_names)
        num_features = random.randint(0, 3)
        room_features = random.sample(feature_names, num_features)
        features_str = "|".join(room_features)
        new_rooms.append([room_name, capacity, zone, features_str])
        existing_room_names.add(room_name)

rooms_data.extend(new_rooms)

# --- Generate Courses ---
print("Generating new courses...")
new_courses = []
lecturer_emails = [row[1] for row in lecturers_data if len(row) > 1 and row[1]]
# Get student groups that are not parent groups
sub_groups = [row for row in student_groups_data if row[2]]
existing_course_codes = {row[0] for row in courses_data}

# Group subgroups by department and year
groups_by_dept_year = {}
for group in sub_groups:
    group_name = group[0]
    parts = group_name.split(' ')
    if "Year" in group_name:
        continue # skip parent groups that might be in sub_groups
    dept = parts[0]
    year = parts[1][0]
    key = (dept, year)
    if key not in groups_by_dept_year:
        groups_by_dept_year[key] = []
    groups_by_dept_year[key].append(group_name)

for key, groups in groups_by_dept_year.items():
    dept, year = key
    # Create 2-3 shared courses for the whole year
    for i in range(random.randint(2,3)):
        course_code = f"{dept.upper()[:4]}{year}S{i+1}"
        if course_code not in existing_course_codes:
            course_name = fake.catch_phrase()
            weekly_hours = random.choice([2, 3, 4])
            lecturer_email = random.choice(lecturer_emails)
            student_group_names = "|".join(groups)
            is_online = random.choice([True, False])
            new_courses.append([course_code, course_name, weekly_hours, lecturer_email, student_group_names, is_online])
            existing_course_codes.add(course_code)

    # Create 3-5 specific courses for each subgroup
    for group in groups:
        for i in range(random.randint(3,5)):
            course_code = f"{group.replace(' ', '')[:6].upper()}{i+1}"
            if course_code not in existing_course_codes:
                course_name = fake.bs()
                weekly_hours = random.choice([2, 3, 4])
                lecturer_email = random.choice(lecturer_emails)
                is_online = random.choice([True, False, False]) # 1/3 chance of being online
                new_courses.append([course_code, course_name, weekly_hours, lecturer_email, group, is_online])
                existing_course_codes.add(course_code)


# Correct the header for courses.csv to match what the backend expects
courses_header = ['code','name','weekly_hours','lecturer_email','student_group_names','is_online']
courses_data.extend(new_courses)

# --- Write all files ---
print("Writing all generated data to CSV files...")
write_csv('csv_files/lecturers.csv', lecturers_header, lecturers_data)
print(f"Wrote {len(lecturers_data)} lecturers.")
write_csv('csv_files/student_groups.csv', student_groups_header, student_groups_data)
print(f"Wrote {len(student_groups_data)} student groups.")
write_csv('csv_files/rooms.csv', rooms_header, rooms_data)
print(f"Wrote {len(rooms_data)} rooms.")
write_csv('csv_files/courses.csv', courses_header, courses_data)
print(f"Wrote {len(courses_data)} courses.")

print("Data generation complete.")