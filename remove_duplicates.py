import re

file_path = "app/src/main/res/values/strings_frontend_modern.xml"
with open(file_path, "r") as f:
    content = f.read()

# We will remove the duplicates from lines 167-197 that we added previously because they already existed at lines 33-41, 96-110, 167-172
lines = content.split('\n')
new_lines = []
seen = set()

for line in lines:
    match = re.search(r'<string name="([^"]+)">', line)
    if match:
        name = match.group(1)
        if name in seen:
            continue
        seen.add(name)
    new_lines.append(line)

with open(file_path, "w") as f:
    f.write('\n'.join(new_lines))
