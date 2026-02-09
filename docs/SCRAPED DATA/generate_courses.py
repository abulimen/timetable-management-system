
import csv
import glob
import os
import re

def normalize_name(name):
    if not name:
        return ""
    # Remove extra internal spaces and strip
    return " ".join(name.split())

def generate_email(name):
    # Logic: abulimensamuel+[cleaned_name]@gmail.com
    # clean: lowercase, replace spaces with .
    cleaned = normalize_name(name).lower().replace(' ', '.')
    cleaned = re.sub(r'[^\w.]', '', cleaned) # Remove other chars if any
    return f"abulimensamuel+{cleaned}@gmail.com"

# 1. Load Lecturers Map
lecturers_map = {}
import_csv_path = 'docs/SCRAPED DATA/REPORT/lecturers_import.csv'

print(f"Loading lecturers from {import_csv_path}...")
try:
    with open(import_csv_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            full_name = f"{row['first_name']} {row['last_name']}"
            norm_name = normalize_name(full_name)
            lecturers_map[norm_name] = row['email']
            lecturers_map[row['email']] = row['email']
except FileNotFoundError:
    print("Error: lecturers_import.csv not found.")
    exit(1)

print(f"Loaded {len(lecturers_map)} lecturers.")

# 2. Process Course Files
scraped_dir = 'docs/SCRAPED DATA'
output_csv = 'docs/SCRAPED DATA/REPORT/courses.csv'

# Columns for output - Added 'year'
fieldnames = ['code', 'name', 'weekly_hours', 'lecturer_email', 'student_group_names', 'year', 'is_online', 'required_features', 'allowed_zones']

courses = []

file_pattern = os.path.join(scraped_dir, '*.csv')
files = glob.glob(file_pattern)

print(f"Found {len(files)} scraped CSV files.")

for file_path in files:
    # Skip creating NEW csv from the REPORT csvs or itself
    if 'REPORT' in file_path:
        continue
        
    filename = os.path.basename(file_path)
    
    print(f"Processing {filename}...")
    
    with open(file_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        
        for row in reader:
            # Map fields
            code = row.get('Course ID', '').strip()
            title = row.get('Course Title', '').strip()
            
            # Skip empty rows or "Citizenship Orientation"
            if not code or not title:
                continue
            
            if "CITIZENSHIP ORIENTATION" in title.upper():
                continue
                
            raw_instructor = row.get('Instuctor', '').strip() # Sic: "Instuctor"
            norm_instructor = normalize_name(raw_instructor)
            
            # Lookup email
            email = lecturers_map.get(norm_instructor)
            if not email:
                email = generate_email(norm_instructor)
            
            hours_str = row.get('Credit Hours', '2').strip()
            try:
                weekly_hours = int(float(hours_str))
            except ValueError:
                weekly_hours = 2
                
            year_str = row.get('Year Taken', '1').strip()
            if not year_str: 
                year_str = '1'

            # Use "Option" column EXACTLY as student_group_names
            student_group = row.get('Option', '').strip()
            if not student_group:
                 # Fallback if Option is empty? Maybe keep it empty or default. 
                 # User emphasized "EXACTLY THE SAME WAY IT IS IN 'OPTION' FIELD".
                 # If it's empty, it's empty.
                 student_group = ""

            # Online Course Logic
            # ALL GEDS and BU-GST courses online! EXCEPT CITIZENSHIP ORIENTATION
            is_online = 'FALSE'
            clean_code = code.upper().strip()
            clean_title = title.strip()
            
            if (clean_code.startswith('GEDS') or clean_code.startswith('BU-GST')):
                # Check for exception
                if "CITIZENSHIP ORIENTATION" not in clean_title.upper():
                    is_online = 'TRUE'

            course = {
                'code': code,
                'name': title,
                'weekly_hours': weekly_hours,
                'lecturer_email': email,
                'student_group_names': student_group,
                'year': year_str,
                'is_online': is_online,
                'required_features': 'Projector', # Default
                'allowed_zones': '' # Default
            }
            
            courses.append(course)

# 3. Write Output
print(f"Writing {len(courses)} courses to {output_csv}...")
with open(output_csv, 'w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(courses)

print("Done.")
